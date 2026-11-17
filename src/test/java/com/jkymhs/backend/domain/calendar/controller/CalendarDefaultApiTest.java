package com.jkymhs.backend.domain.calendar.controller;

import com.jkymhs.backend.domain.calendar.CalendarApiTestSupport;
import com.jkymhs.backend.domain.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CalendarDefaultApiTest extends CalendarApiTestSupport {

    private static final String LIST_URL = API + "/users/me/calendarList?minAccessRole=reader&maxResults=250";

    @Test
    @DisplayName("쓸 수 있는 캘린더로 바꾸면 기본 캘린더가 된다")
    void change() throws Exception {
        User user = saveUser("default@example.com");
        link(user);
        expectList();

        changeDefault(user, "family@group.calendar.google.com")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.defaultCalendarId").value("family@group.calendar.google.com"));
        assertThat(linkRepository.findByUserId(user.getId()).orElseThrow().getDefaultCalendarId())
                .isEqualTo("family@group.calendar.google.com");
    }

    @Test
    @DisplayName("읽기 전용이거나 목록에 없는 캘린더는 400 CALENDAR_NOT_WRITABLE")
    void notWritable() throws Exception {
        User user = saveUser("readonly@example.com");
        link(user);
        expectList();
        changeDefault(user, "ko.south_korea#holiday@group.v.calendar.google.com")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CALENDAR_NOT_WRITABLE"))
                .andExpect(jsonPath("$.message").value("선택한 캘린더에 일정을 쓸 수 없습니다."));

        google.reset();
        expectList();
        changeDefault(user, "unknown@group.calendar.google.com").andExpect(status().isBadRequest());
        assertThat(linkRepository.findByUserId(user.getId()).orElseThrow().getDefaultCalendarId()).isEqualTo("primary");
    }

    @Test
    @DisplayName("primary는 구글을 부르지 않고 받는다. 연동이 없으면 403")
    void primaryAndNotLinked() throws Exception {
        User user = saveUser("primary@example.com");
        changeDefault(user, "primary").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CALENDAR_NOT_LINKED"));

        link(user);
        changeDefault(user, "primary").andExpect(status().isOk())
                .andExpect(jsonPath("$.defaultCalendarId").value("primary"));
        google.verify();
    }

    private void expectList() {
        expectRefresh();
        expectGoogle(HttpMethod.GET, LIST_URL).andRespond(withSuccess(calendarListJson(), MediaType.APPLICATION_JSON));
    }

    private ResultActions changeDefault(User user, String calendarId) throws Exception {
        return mockMvc.perform(json(put("/api/calendar/default"), token(user), """
                {"calendarId": "%s"}""".formatted(calendarId)));
    }
}
