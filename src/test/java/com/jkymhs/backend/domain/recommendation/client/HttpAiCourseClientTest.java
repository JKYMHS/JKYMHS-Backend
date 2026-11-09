package com.jkymhs.backend.domain.recommendation.client;

import com.jkymhs.backend.domain.recommendation.client.ai.AiCourseException;
import com.jkymhs.backend.domain.recommendation.client.ai.AiCourseRequest;
import com.jkymhs.backend.domain.recommendation.client.ai.AiCourseResponse;
import com.jkymhs.backend.domain.recommendation.client.ai.AiProperties;
import com.jkymhs.backend.domain.recommendation.client.ai.AiTimeoutException;
import com.jkymhs.backend.domain.recommendation.client.ai.HttpAiCourseClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 실제 AI 서버 대신 MockRestServiceServer가 응답한다 (스프링 컨텍스트 없이).
 */
class HttpAiCourseClientTest {

    private static final String BASE_URL = "https://ai.test";

    private MockRestServiceServer server;
    private HttpAiCourseClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new HttpAiCourseClient(builder, new AiProperties("http", BASE_URL, "ai-key", Duration.ofSeconds(20)));
    }

    @Test
    @DisplayName("POST /v1/courses에 Bearer 키와 계약대로의 JSON(HH:mm 시각, 날짜, 후보, 규칙)을 보내고 응답을 읽는다")
    void contract() {
        server.expect(requestTo(BASE_URL + "/v1/courses"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer ai-key"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.requestId").value(7))
                .andExpect(jsonPath("$.conditions.startDate").value("2026-11-09"))
                .andExpect(jsonPath("$.conditions.startTime").value("10:00"))
                .andExpect(jsonPath("$.conditions.companion").value("kids"))
                .andExpect(jsonPath("$.conditions.pace").value("relaxed"))
                .andExpect(jsonPath("$.profile.topCategories[0].lcls2").value("HS01"))
                .andExpect(jsonPath("$.candidates[0].contentId").value(1001))
                .andExpect(jsonPath("$.candidates[0].openTime").value("09:00"))
                .andExpect(jsonPath("$.candidates[0].restDays[0]").value("TUE"))
                .andExpect(jsonPath("$.candidates[0].required").value(true))
                .andExpect(jsonPath("$.rules.courses").value(3))
                .andExpect(jsonPath("$.rules.placesPerDay.min").value(1))
                .andExpect(jsonPath("$.rules.lunch").value("11:30-13:30"))
                .andExpect(jsonPath("$.rules.noPlaceAfter").value("20:00"))
                .andExpect(jsonPath("$.violations[0]").value("코스 1: 후보에 없는 장소입니다: 9"))
                .andRespond(withSuccess("""
                        {"courses": [{"title": "가족 코스", "summary": "궁과 박물관",
                          "days": [{"dayNumber": 1, "items": [{"contentId": 1001, "stayMinutes": 80, "reason": "아이와 걷기 좋음"}]}]}]}
                        """, MediaType.APPLICATION_JSON));

        AiCourseResponse response = client.compose(request());

        assertThat(response.courses()).singleElement().satisfies(course -> {
            assertThat(course.title()).isEqualTo("가족 코스");
            assertThat(course.days().getFirst().items().getFirst().stayMinutes()).isEqualTo(80);
        });
        server.verify();
    }

    @Test
    @DisplayName("응답 대기 시간을 넘기면 AiTimeoutException, 5xx면 AiCourseException이다")
    void failures() {
        server.expect(requestTo(BASE_URL + "/v1/courses")).andRespond(withException(new SocketTimeoutException("read")));
        assertThatThrownBy(() -> client.compose(request())).isInstanceOf(AiTimeoutException.class);

        server.reset();
        server.expect(requestTo(BASE_URL + "/v1/courses")).andRespond(withServerError());
        assertThatThrownBy(() -> client.compose(request()))
                .isInstanceOf(AiCourseException.class)
                .isNotInstanceOf(AiTimeoutException.class);
    }

    @Test
    @DisplayName("provider=http인데 주소가 없으면 설정 단계에서 실패한다")
    void httpRequiresBaseUrl() {
        assertThatThrownBy(() -> new AiProperties("http", " ", null, Duration.ofSeconds(20)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ai.base-url");
        assertThat(new AiProperties("mock", null, null, Duration.ofSeconds(20)).hasApiKey()).isFalse();
    }

    private static AiCourseRequest request() {
        LocalDate day = LocalDate.of(2026, 11, 9);
        return new AiCourseRequest(7L,
                new AiCourseRequest.Conditions(day, day, LocalTime.of(10, 0), "kids", "ELEMENTARY", "relaxed", false,
                        "morning", new AiCourseRequest.Point(37.55, 126.97)),
                new AiCourseRequest.Profile(List.of(new AiCourseRequest.TopCategory("HS01", 3.5))),
                List.of(new AiCourseRequest.Candidate(1001L, "경복궁", 37.57, 126.97, "궁", "OUTDOOR",
                        LocalTime.of(9, 0), LocalTime.of(18, 0), LocalTime.of(17, 0), List.of("TUE"), null, null, true)),
                new AiCourseRequest.Rules(3, new AiCourseRequest.PlacesPerDay(1, 2), "11:30-13:30", "17:30-19:30",
                        "20:00"),
                List.of("코스 1: 후보에 없는 장소입니다: 9"));
    }
}
