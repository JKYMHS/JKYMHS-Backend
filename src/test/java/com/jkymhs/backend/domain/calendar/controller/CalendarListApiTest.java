package com.jkymhs.backend.domain.calendar.controller;

import com.jkymhs.backend.domain.calendar.CalendarApiTestSupport;
import com.jkymhs.backend.domain.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CalendarListApiTest extends CalendarApiTestSupport {

    private static final String LIST_URL = API + "/users/me/calendarList?minAccessRole=reader&maxResults=250";

    @Test
    @DisplayName("쓸 수 있는 캘린더(owner·writer)만 준다. 이름은 사용자가 바꾼 이름(summaryOverride)이 먼저다")
    void writableOnly() throws Exception {
        User user = saveUser("list@example.com");
        link(user);
        expectRefresh();
        expectGoogle(HttpMethod.GET, LIST_URL).andRespond(withSuccess(calendarListJson(), MediaType.APPLICATION_JSON));

        mockMvc.perform(auth(get("/api/calendar/calendars"), token(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.calendars.length()").value(2))
                .andExpect(jsonPath("$.calendars[0].id").value("me@gmail.com"))
                .andExpect(jsonPath("$.calendars[0].primary").value(true))
                .andExpect(jsonPath("$.calendars[0].writable").value(true))
                .andExpect(jsonPath("$.calendars[1].id").value("family@group.calendar.google.com"))
                .andExpect(jsonPath("$.calendars[1].name").value("우리 가족"))
                .andExpect(jsonPath("$.calendars[1].primary").value(false));
        google.verify();
    }

    @Test
    @DisplayName("리프레시 토큰이 거부되면(invalid_grant) 401 CALENDAR_AUTH_EXPIRED이고 연동이 REVOKED로 바뀐다")
    void invalidGrant() throws Exception {
        User user = saveUser("expired@example.com");
        link(user);
        google.expect(requestTo(TOKEN_URI)).andRespond(withStatus(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\": \"invalid_grant\", \"error_description\": \"Token has been expired or revoked.\"}"));

        mockMvc.perform(auth(get("/api/calendar/calendars"), token(user)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("CALENDAR_AUTH_EXPIRED"))
                .andExpect(jsonPath("$.message").value("구글 캘린더 연결이 만료되었습니다. 다시 연동해주세요."));

        assertThat(linkStatus(user.getId())).isEqualTo("REVOKED");
        // 만료된 연동으로는 다시 불러도 구글을 부르지 않고 403
        mockMvc.perform(auth(get("/api/calendar/calendars"), token(user)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CALENDAR_NOT_LINKED"));
    }

    @Test
    @DisplayName("연동하지 않았으면 403 CALENDAR_NOT_LINKED, 구글 5xx면 502 GOOGLE_SERVER_ERROR")
    void notLinkedAndServerError() throws Exception {
        User user = saveUser("nolink@example.com");
        mockMvc.perform(auth(get("/api/calendar/calendars"), token(user)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CALENDAR_NOT_LINKED"))
                .andExpect(jsonPath("$.message").value("구글 캘린더를 먼저 연동해주세요."));

        link(user);
        expectRefresh();
        expectGoogle(HttpMethod.GET, LIST_URL).andRespond(withServerError());
        mockMvc.perform(auth(get("/api/calendar/calendars"), token(user)))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("GOOGLE_SERVER_ERROR"));
    }
}
