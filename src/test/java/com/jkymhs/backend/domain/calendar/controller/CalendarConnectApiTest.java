package com.jkymhs.backend.domain.calendar.controller;

import com.jkymhs.backend.domain.calendar.CalendarApiTestSupport;
import com.jkymhs.backend.domain.calendar.entity.GoogleCalendarLink;
import com.jkymhs.backend.domain.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CalendarConnectApiTest extends CalendarApiTestSupport {

    private static final String SCOPES = "openid https://www.googleapis.com/auth/userinfo.email "
            + "https://www.googleapis.com/auth/calendar.events https://www.googleapis.com/auth/calendar.calendarlist.readonly";

    private User user;
    private String token;

    @BeforeEach
    void setUp() {
        user = saveUser("calendar@example.com");
        token = token(user);
    }

    @Test
    @DisplayName("인가 코드를 토큰으로 바꾸고 이메일을 읽어 연동한다. 리프레시 토큰은 암호화해서 저장한다")
    void connect() throws Exception {
        expectExchange(SCOPES, "\"refresh_token\": \"google-refresh\",");
        expectUserInfo();

        connect("code-1")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.linked").value(true))
                .andExpect(jsonPath("$.googleEmail").value(GOOGLE_EMAIL))
                .andExpect(jsonPath("$.defaultCalendarId").value("primary"));

        google.verify();
        GoogleCalendarLink link = linkRepository.findByUserId(user.getId()).orElseThrow();
        assertThat(link.getEncryptedRefreshToken()).doesNotContain("google-refresh");
        assertThat(tokenCipher.decrypt(link.getEncryptedRefreshToken())).isEqualTo("google-refresh");
        assertThat(link.getGrantedScopes()).contains("https://www.googleapis.com/auth/calendar.events");
        assertThat(link.isActive()).isTrue();
    }

    @Test
    @DisplayName("캘린더 권한 체크를 끄고 동의하면 400 CALENDAR_PERMISSION_DENIED이고 저장하지 않는다")
    void missingScope() throws Exception {
        expectExchange("openid https://www.googleapis.com/auth/calendar.events", "\"refresh_token\": \"r\",");

        connect("code-1")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CALENDAR_PERMISSION_DENIED"))
                .andExpect(jsonPath("$.message").value("캘린더 권한을 허용해야 연동할 수 있습니다."));
        assertThat(linkCount()).isZero();
    }

    @Test
    @DisplayName("리프레시 토큰이 오지 않으면(offline 동의가 아님) 400 CALENDAR_PERMISSION_DENIED")
    void noRefreshToken() throws Exception {
        expectExchange(SCOPES, "");

        connect("code-1")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CALENDAR_PERMISSION_DENIED"));
        assertThat(linkCount()).isZero();
    }

    @Test
    @DisplayName("구글이 인가 코드를 거부하면(4xx) 401 GOOGLE_AUTH_FAILED, 5xx면 502 GOOGLE_SERVER_ERROR")
    void googleErrors() throws Exception {
        google.expect(requestTo(TOKEN_URI)).andRespond(withStatus(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON).body("{\"error\": \"invalid_grant\"}"));
        connect("used-code")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("GOOGLE_AUTH_FAILED"))
                .andExpect(jsonPath("$.message").value("구글 인증에 실패했습니다. 다시 시도해주세요."));

        google.reset();
        google.expect(requestTo(TOKEN_URI)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        connect("code-2")
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("GOOGLE_SERVER_ERROR"));
        assertThat(linkCount()).isZero();
    }

    @Test
    @DisplayName("다시 연동하면 같은 행의 토큰을 바꾸고 ACTIVE로 되돌린다. 다른 구글 계정이면 기본 캘린더를 primary로 되돌린다")
    void relink() throws Exception {
        GoogleCalendarLink link = link(user);
        jdbcTemplate.update("UPDATE google_calendar_links SET status = 'REVOKED', default_calendar_id = 'family@group' "
                + "WHERE id = ?", link.getId());
        expectExchange(SCOPES, "\"refresh_token\": \"new-refresh\",");
        google.expect(requestTo(USERINFO_URI)).andRespond(withSuccess("{\"email\": \"other@gmail.com\"}",
                MediaType.APPLICATION_JSON));

        connect("code-1")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.googleEmail").value("other@gmail.com"))
                .andExpect(jsonPath("$.defaultCalendarId").value("primary"));

        assertThat(linkCount()).isEqualTo(1);
        GoogleCalendarLink updated = linkRepository.findByUserId(user.getId()).orElseThrow();
        assertThat(updated.getId()).isEqualTo(link.getId());
        assertThat(updated.isActive()).isTrue();
        assertThat(tokenCipher.decrypt(updated.getEncryptedRefreshToken())).isEqualTo("new-refresh");
    }

    @Test
    @DisplayName("인가 코드가 비어 있으면 400 INVALID_INPUT")
    void validation() throws Exception {
        mockMvc.perform(json(post("/api/calendar"), token, """
                        {"code": "", "redirectUri": "http://localhost:3000/oauth/calendar"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.message").value("인가 코드를 입력해주세요."));
    }

    private void expectExchange(String scope, String refreshTokenField) {
        google.expect(requestTo(TOKEN_URI)).andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("grant_type=authorization_code")))
                .andExpect(content().string(containsString("code=code-1")))
                .andRespond(withSuccess("""
                        {"access_token": "%s", %s "scope": "%s", "expires_in": 3599}
                        """.formatted(ACCESS_TOKEN, refreshTokenField, scope), MediaType.APPLICATION_JSON));
    }

    private void expectUserInfo() {
        google.expect(requestTo(USERINFO_URI))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN))
                .andRespond(withSuccess("{\"sub\": \"1\", \"email\": \"%s\"}".formatted(GOOGLE_EMAIL),
                        MediaType.APPLICATION_JSON));
    }

    private ResultActions connect(String code) throws Exception {
        return mockMvc.perform(json(post("/api/calendar"), token, """
                {"code": "%s", "redirectUri": "http://localhost:3000/oauth/calendar"}""".formatted(code)));
    }
}
