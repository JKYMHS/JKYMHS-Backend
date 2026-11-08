package com.jkymhs.backend.domain.content.client;

import com.jkymhs.backend.domain.content.service.ContentRedisKeys;
import com.jkymhs.backend.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Map;

import static com.jkymhs.backend.domain.content.client.TourApiResponses.item;
import static com.jkymhs.backend.domain.content.client.TourApiResponses.page;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 실제 TourAPI 대신 MockRestServiceServer가 응답한다. 호출 수는 실제 Redis(Testcontainers)에 센다.
 */
class TourApiClientTest extends IntegrationTestSupport {

    private static final String BASE_URL = "https://tour.test/KorService2";

    @Autowired
    private Clock clock;

    private MockRestServiceServer server;

    private TourApiClient client(int dailyLimit, String serviceKey) {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        TourApiProperties properties = new TourApiProperties(BASE_URL, serviceKey, dailyLimit, Duration.ofSeconds(10),
                Duration.ZERO, new TourApiProperties.Sync("0 0 4 * * *", 300));
        return new TourApiClient(builder, properties, redisTemplate, clock);
    }

    @Test
    @DisplayName("공통 파라미터와 Decoding 키를 한 번만 인코딩해서 보내고, 항목을 문자열 맵으로 읽는다")
    void requestAndParse() {
        TourApiClient client = client(1000, "ab+c/d==");
        server.expect(requestTo(startsWith(BASE_URL + "/areaBasedSyncList2?")))
                .andExpect(requestTo(containsString("serviceKey=ab%2Bc%2Fd%3D%3D")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(queryParam("MobileOS", "ETC"))
                .andExpect(queryParam("MobileApp", "EDGL"))
                .andExpect(queryParam("_type", "json"))
                .andExpect(queryParam("contentTypeId", "12"))
                .andExpect(queryParam("modifiedtime", "20261101"))
                .andExpect(queryParam("numOfRows", "1000"))
                .andRespond(withSuccess(page(1, 1500, item("contentid", "126508", "title", "경복궁", "showflag", "1")),
                        MediaType.APPLICATION_JSON));

        TourApiPage result = client.areaBasedSyncList(12, LocalDate.of(2026, 11, 1), 1);

        server.verify();
        assertThat(result.items()).containsExactly(Map.of("contentid", "126508", "title", "경복궁", "showflag", "1"));
        assertThat(result.totalCount()).isEqualTo(1500);
        assertThat(result.hasNext()).isTrue();
    }

    @Test
    @DisplayName("결과가 없으면 items가 \"\"로 오고, 한 건이면 item이 객체로 와도 읽는다")
    void emptyAndSingleItem() {
        TourApiClient client = client(1000, "key");
        server.expect(requestTo(startsWith(BASE_URL + "/detailIntro2?")))
                .andRespond(withSuccess(page(), MediaType.APPLICATION_JSON));
        server.expect(requestTo(startsWith(BASE_URL + "/detailIntro2?")))
                .andRespond(withSuccess("""
                        {"response":{"header":{"resultCode":"0000","resultMsg":"OK"},
                         "body":{"items":{"item":{"contentid":"1","usetime":"09:00~18:00"}},"numOfRows":10,"pageNo":1,"totalCount":1}}}
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.detailIntro(1L, 12)).isNull();
        assertThat(client.detailIntro(1L, 12)).containsEntry("usetime", "09:00~18:00");
    }

    @Test
    @DisplayName("호출할 때마다 그날 호출 수를 Redis에 세고 TTL을 건다")
    void countsCalls() {
        TourApiClient client = client(1000, "key");
        server.expect(ExpectedCount.times(2), requestTo(startsWith(BASE_URL + "/detailCommon2?")))
                .andRespond(withSuccess(page(), MediaType.APPLICATION_JSON));

        client.detailCommon(1L);
        client.detailCommon(2L);

        String key = ContentRedisKeys.tourApiCalls(LocalDate.now(clock));
        assertThat(redisTemplate.opsForValue().get(key)).isEqualTo("2");
        assertThat(redisTemplate.getExpire(key)).isPositive();
        assertThat(client.todayCallCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("오늘 호출 수가 한도에 닿으면 TourAPI를 부르지 않고 한도 초과 예외를 던진다")
    void dailyLimit() {
        TourApiClient client = client(2, "key");
        server.expect(ExpectedCount.times(2), requestTo(startsWith(BASE_URL + "/detailCommon2?")))
                .andRespond(withSuccess(page(), MediaType.APPLICATION_JSON));

        client.detailCommon(1L);
        client.detailCommon(2L);

        assertThatThrownBy(() -> client.detailCommon(3L)).isInstanceOf(TourApiQuotaExceededException.class);
        server.verify();
    }

    @Test
    @DisplayName("5xx·타임아웃은 3번까지 다시 시도하고, 그래도 실패하면 TourApiException이다")
    void retryOnServerError() {
        TourApiClient client = client(1000, "key");
        server.expect(requestTo(startsWith(BASE_URL + "/detailCommon2?"))).andRespond(withServerError());
        server.expect(requestTo(startsWith(BASE_URL + "/detailCommon2?")))
                .andRespond(withException(new SocketTimeoutException("read timed out")));
        server.expect(requestTo(startsWith(BASE_URL + "/detailCommon2?")))
                .andRespond(withSuccess(page(item("contentid", "1", "overview", "소개")), MediaType.APPLICATION_JSON));

        assertThat(client.detailCommon(1L)).containsEntry("overview", "소개");
        server.verify();

        TourApiClient failing = client(1000, "key");
        server.expect(ExpectedCount.times(3), requestTo(startsWith(BASE_URL + "/detailCommon2?")))
                .andRespond(withServerError());
        assertThatThrownBy(() -> failing.detailCommon(1L))
                .isInstanceOf(TourApiException.class)
                .isNotInstanceOf(TourApiQuotaExceededException.class);
        server.verify();
    }

    @Test
    @DisplayName("XML 오류 응답(한도 초과)과 resultCode 22는 한도 초과로, 그 밖의 오류 코드는 TourApiException으로 본다")
    void errorResponses() {
        TourApiClient client = client(1000, "key");
        server.expect(requestTo(startsWith(BASE_URL + "/detailCommon2?")))
                .andRespond(withSuccess(TourApiResponses.QUOTA_XML, MediaType.TEXT_XML));
        server.expect(requestTo(startsWith(BASE_URL + "/detailCommon2?")))
                .andRespond(withSuccess("""
                        {"response":{"header":{"resultCode":"22","resultMsg":"LIMITED_NUMBER_OF_SERVICE_REQUESTS_EXCEEDS_ERROR"}}}
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo(startsWith(BASE_URL + "/detailCommon2?")))
                .andRespond(withSuccess("""
                        {"response":{"header":{"resultCode":"10","resultMsg":"INVALID_REQUEST_PARAMETER_ERROR"}}}
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.detailCommon(1L)).isInstanceOf(TourApiQuotaExceededException.class);
        assertThatThrownBy(() -> client.detailCommon(1L)).isInstanceOf(TourApiQuotaExceededException.class);
        assertThatThrownBy(() -> client.detailCommon(1L))
                .isInstanceOf(TourApiException.class)
                .isNotInstanceOf(TourApiQuotaExceededException.class)
                .hasMessageContaining("INVALID_REQUEST_PARAMETER_ERROR");
    }

    @Test
    @DisplayName("반려동물 동반 구분을 true/false/null로 바꾼다")
    void petAllowed() {
        assertThat(TourApiItems.petAllowed(Map.of("acmpyTypeCd", "동반가능"))).isTrue();
        assertThat(TourApiItems.petAllowed(Map.of("acmpyTypeCd", "일부구역 동반가능"))).isTrue();
        assertThat(TourApiItems.petAllowed(Map.of("acmpyTypeCd", "동반불가"))).isFalse();
        assertThat(TourApiItems.petAllowed(Map.of())).isNull();
        assertThat(TourApiItems.petAllowed(null)).isNull();
    }
}
