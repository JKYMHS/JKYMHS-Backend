package com.jkymhs.backend.domain.auth.client;

import com.jkymhs.backend.global.exception.CustomException;
import com.jkymhs.backend.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 실제 구글 서버 대신 MockRestServiceServer가 응답하게 해서, 구글 응답별로 어떤 예외가 나가는지 확인한다.
 * 스프링 컨텍스트를 띄우지 않는 단위 테스트다.
 */
class GoogleOAuthClientTest {

    private static final String TOKEN_URI = "https://oauth2.googleapis.com/token";
    private static final String USER_INFO_URI = "https://openidconnect.googleapis.com/v1/userinfo";
    private static final GoogleOAuthProperties PROPERTIES =
            new GoogleOAuthProperties("test-client-id", "test-client-secret", TOKEN_URI, USER_INFO_URI);

    // 실제 구글 인가 코드처럼 '/'를 넣어서 form 인코딩(%2F)이 제대로 되는지도 본다
    private static final String CODE = "4/0AX4XfWh-test-code";
    private static final String REDIRECT_URI = "https://whereto.app/oauth/google";

    private static final String TOKEN_RESPONSE = """
            {"access_token":"google-access-token","expires_in":3599,"scope":"openid email profile",
             "token_type":"Bearer","id_token":"header.payload.signature"}
            """;

    private MockRestServiceServer server;
    private GoogleOAuthClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new GoogleOAuthClient(builder, PROPERTIES);
    }

    @Test
    @DisplayName("인가 코드를 form으로 보내 토큰을 받고, 그 토큰으로 사용자 정보를 읽는다 (이메일은 소문자로 맞춘다)")
    void fetchUserInfo() {
        server.expect(requestTo(TOKEN_URI))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().formDataContains(Map.of(
                        "code", CODE,
                        "client_id", "test-client-id",
                        "client_secret", "test-client-secret",
                        "redirect_uri", REDIRECT_URI,
                        "grant_type", "authorization_code"
                )))
                .andRespond(withSuccess(TOKEN_RESPONSE, MediaType.APPLICATION_JSON));
        server.expect(requestTo(USER_INFO_URI))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer google-access-token"))
                .andRespond(withSuccess("""
                        {"sub":"10769150350006150715","email":"Yunho@Gmail.com","email_verified":true,
                         "name":"윤호","picture":"https://example.com/p.png"}
                        """, MediaType.APPLICATION_JSON));

        GoogleUserInfo userInfo = client.fetchUserInfo(CODE, REDIRECT_URI);

        assertThat(userInfo.sub()).isEqualTo("10769150350006150715");
        assertThat(userInfo.email()).isEqualTo("yunho@gmail.com");
        server.verify();
    }

    @Test
    @DisplayName("구글이 토큰 교환을 400(invalid_grant: 만료·재사용된 코드)으로 거부하면 401")
    void invalidGrant() {
        server.expect(requestTo(TOKEN_URI))
                .andRespond(withBadRequest()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":\"invalid_grant\",\"error_description\":\"Bad Request\"}"));

        assertThatThrownBy(() -> client.fetchUserInfo(CODE, REDIRECT_URI))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> {
                    CustomException ce = (CustomException) e;
                    assertThat(ce.getErrorCode()).isEqualTo(ErrorCode.GOOGLE_AUTH_FAILED);
                    assertThat(ce.getDescription()).isEqualTo("인가 코드 만료·재사용");
                });
    }

    @Test
    @DisplayName("구글 서버가 500을 주면 502")
    void googleServerError() {
        server.expect(requestTo(TOKEN_URI)).andRespond(withServerError());

        assertThatThrownBy(() -> client.fetchUserInfo(CODE, REDIRECT_URI))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.GOOGLE_SERVER_ERROR);
    }

    @Test
    @DisplayName("구글이 응답하지 않아 타임아웃이 나면 502")
    void timeout() {
        server.expect(requestTo(TOKEN_URI)).andRespond(request -> {
            throw new SocketTimeoutException("Read timed out");
        });

        assertThatThrownBy(() -> client.fetchUserInfo(CODE, REDIRECT_URI))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.GOOGLE_SERVER_ERROR);
    }

    @Test
    @DisplayName("사용자 정보 조회에서 구글이 500을 주면 502")
    void userInfoServerError() {
        server.expect(requestTo(TOKEN_URI)).andRespond(withSuccess(TOKEN_RESPONSE, MediaType.APPLICATION_JSON));
        server.expect(requestTo(USER_INFO_URI)).andRespond(withServerError());

        assertThatThrownBy(() -> client.fetchUserInfo(CODE, REDIRECT_URI))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.GOOGLE_SERVER_ERROR);
    }

    @Test
    @DisplayName("구글 이메일이 인증되지 않은 계정(email_verified=false)이면 401")
    void emailNotVerified() {
        server.expect(requestTo(TOKEN_URI)).andRespond(withSuccess(TOKEN_RESPONSE, MediaType.APPLICATION_JSON));
        server.expect(requestTo(USER_INFO_URI)).andRespond(withSuccess("""
                {"sub":"10769150350006150715","email":"yunho@gmail.com","email_verified":false}
                """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.fetchUserInfo(CODE, REDIRECT_URI))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> {
                    CustomException ce = (CustomException) e;
                    assertThat(ce.getErrorCode()).isEqualTo(ErrorCode.GOOGLE_AUTH_FAILED);
                    assertThat(ce.getDescription()).isEqualTo("인증되지 않은 구글 이메일");
                });
    }

    @Test
    @DisplayName("email scope 없이 요청해서 이메일이 오지 않으면 401")
    void noEmail() {
        server.expect(requestTo(TOKEN_URI)).andRespond(withSuccess(TOKEN_RESPONSE, MediaType.APPLICATION_JSON));
        server.expect(requestTo(USER_INFO_URI)).andRespond(withSuccess("""
                {"sub":"10769150350006150715"}
                """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.fetchUserInfo(CODE, REDIRECT_URI))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.GOOGLE_AUTH_FAILED);
    }
}
