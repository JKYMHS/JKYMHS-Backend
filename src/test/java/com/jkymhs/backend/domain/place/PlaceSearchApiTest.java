package com.jkymhs.backend.domain.place;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PlaceSearchApiTest extends KakaoLocalTestSupport {

    private static final String KEYWORD_BODY = """
            {"documents":[
              {"id":"21160","place_name":"용산역","address_name":"서울 용산구 한강로3가 40-999",
               "road_address_name":"서울 용산구 한강대로23길 55","category_group_name":"지하철역","x":"126.96477","y":"37.52990"},
              {"id":"8082","place_name":"용산역 광장","address_name":"서울 용산구 한강로3가 40-1",
               "road_address_name":"","category_group_name":"","x":"126.96500","y":"37.52900"}
            ],"meta":{"total_count":2}}
            """;

    private static final String ADDRESS_BODY = """
            {"documents":[
              {"address_name":"서울 용산구 한강로3가 40-999","x":"126.96477","y":"37.52990",
               "address":{"address_name":"서울 용산구 한강로3가 40-999"},"road_address":null},
              {"address_name":"서울 용산구 한강대로 405","x":"126.97","y":"37.55",
               "address":{"address_name":"서울 중구 봉래동2가 122"},"road_address":{"address_name":"서울 용산구 한강대로 405"}}
            ],"meta":{"total_count":2}}
            """;

    @Test
    @DisplayName("키워드 검색 결과 뒤에 주소 검색 결과를 붙이고, 이미 나온 주소는 뺀다")
    void mergeKeywordAndAddress() throws Exception {
        kakao.expect(requestTo(startsWith(BASE_URL + "/v2/local/search/keyword.json")))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "KakaoAK " + API_KEY))
                .andRespond(withSuccess(KEYWORD_BODY, MediaType.APPLICATION_JSON));
        kakao.expect(requestTo(startsWith(BASE_URL + "/v2/local/search/address.json")))
                .andRespond(withSuccess(ADDRESS_BODY, MediaType.APPLICATION_JSON));

        call(get("/api/places/search").param("q", "용산역"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].name").value(contains("용산역", "용산역 광장", "서울 용산구 한강대로 405")))
                .andExpect(jsonPath("$.items[0].placeId").value("21160"))
                .andExpect(jsonPath("$.items[0].address").value("서울 용산구 한강로3가 40-999"))
                .andExpect(jsonPath("$.items[0].roadAddress").value("서울 용산구 한강대로23길 55"))
                .andExpect(jsonPath("$.items[0].category").value("지하철역"))
                .andExpect(jsonPath("$.items[0].lat").value(37.5299))
                .andExpect(jsonPath("$.items[0].lng").value(126.96477))
                .andExpect(jsonPath("$.items[1].roadAddress").value(nullValue()))
                .andExpect(jsonPath("$.items[1].category").value(nullValue()))
                .andExpect(jsonPath("$.items[2].placeId").value(nullValue()))
                .andExpect(jsonPath("$.items[2].address").value("서울 중구 봉래동2가 122"))
                .andExpect(jsonPath("$.items[2].roadAddress").value("서울 용산구 한강대로 405"));
        kakao.verify();
    }

    @Test
    @DisplayName("카카오가 500이면 502 '주소 검색에 실패했습니다. 다시 시도해주세요.'다")
    void kakaoServerError() throws Exception {
        kakao.expect(requestTo(startsWith(BASE_URL + "/v2/local/search/keyword.json"))).andRespond(withServerError());

        call(get("/api/places/search").param("q", "용산역"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("EXTERNAL_API_ERROR"))
                .andExpect(jsonPath("$.message").value("주소 검색에 실패했습니다. 다시 시도해주세요."));
        kakao.verify();
    }

    @Test
    @DisplayName("카카오 키가 없으면 부르지 않고 502다")
    void blankKey() throws Exception {
        useClient("");

        call(get("/api/places/search").param("q", "용산역"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("EXTERNAL_API_ERROR"))
                .andExpect(jsonPath("$.message").value("주소 검색에 실패했습니다. 다시 시도해주세요."));
        kakao.verify();
    }

    @Test
    @DisplayName("검색어가 없거나 2자 미만이면 400이다")
    void tooShort() throws Exception {
        call(get("/api/places/search").param("q", " 용 "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.message").value("검색어는 2자 이상 입력해주세요."));
        call(get("/api/places/search"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.message").value("검색어는 2자 이상 입력해주세요."));
        kakao.verify();
    }

    @Test
    @DisplayName("로그인하지 않으면 401이고 카카오를 부르지 않는다")
    void unauthorized() throws Exception {
        mockMvc.perform(get("/api/places/search").param("q", "용산역"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("인증이 필요합니다."));
        kakao.verify();
    }
}
