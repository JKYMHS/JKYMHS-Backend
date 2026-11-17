package com.jkymhs.backend.domain.place;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReverseGeocodeApiTest extends KakaoLocalTestSupport {

    private static final String COORD_PATH = BASE_URL + "/v2/local/geo/coord2address.json";

    @Test
    @DisplayName("좌표를 x=경도, y=위도로 넘기고, 건물 이름이 있으면 그것을 이름으로 준다")
    void withBuilding() throws Exception {
        kakao.expect(requestTo(startsWith(COORD_PATH)))
                .andExpect(queryParam("x", "126.9648"))
                .andExpect(queryParam("y", "37.5299"))
                .andRespond(withSuccess("""
                        {"documents":[{"address":{"address_name":"서울 용산구 한강로3가 40-999"},
                          "road_address":{"address_name":"서울 용산구 한강대로23길 55","building_name":"용산역"}}],
                         "meta":{"total_count":1}}
                        """, MediaType.APPLICATION_JSON));

        call(get("/api/places/reverse-geocode").param("lat", "37.5299").param("lng", "126.9648"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("용산역"))
                .andExpect(jsonPath("$.address").value("서울 용산구 한강로3가 40-999"))
                .andExpect(jsonPath("$.roadAddress").value("서울 용산구 한강대로23길 55"));
        kakao.verify();
    }

    @Test
    @DisplayName("도로명 주소가 없으면 지번 주소를 이름으로 쓴다")
    void withoutRoadAddress() throws Exception {
        kakao.expect(requestTo(startsWith(COORD_PATH)))
                .andRespond(withSuccess("""
                        {"documents":[{"address":{"address_name":"강원 평창군 대관령면 횡계리 1"},"road_address":null}],
                         "meta":{"total_count":1}}
                        """, MediaType.APPLICATION_JSON));

        call(get("/api/places/reverse-geocode").param("lat", "37.68").param("lng", "128.72"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("강원 평창군 대관령면 횡계리 1"))
                .andExpect(jsonPath("$.roadAddress").value(nullValue()));
    }

    @Test
    @DisplayName("주소가 없는 좌표(바다 등)나 카카오 오류는 502 '주소를 찾지 못했습니다. 다시 시도해주세요.'다")
    void notFoundOrError() throws Exception {
        kakao.expect(requestTo(startsWith(COORD_PATH)))
                .andRespond(withSuccess("{\"documents\":[],\"meta\":{\"total_count\":0}}", MediaType.APPLICATION_JSON));
        kakao.expect(requestTo(startsWith(COORD_PATH))).andRespond(withServerError());

        call(get("/api/places/reverse-geocode").param("lat", "35.0").param("lng", "130.0"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("EXTERNAL_API_ERROR"))
                .andExpect(jsonPath("$.message").value("주소를 찾지 못했습니다. 다시 시도해주세요."));
        call(get("/api/places/reverse-geocode").param("lat", "37.5").param("lng", "127.0"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("EXTERNAL_API_ERROR"))
                .andExpect(jsonPath("$.message").value("주소를 찾지 못했습니다. 다시 시도해주세요."));
        kakao.verify();
    }

    @Test
    @DisplayName("좌표가 없거나 범위를 벗어나면 400이다")
    void invalidCoordinates() throws Exception {
        call(get("/api/places/reverse-geocode").param("lat", "95").param("lng", "127"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.message").value("좌표 값이 올바르지 않습니다."));
        call(get("/api/places/reverse-geocode").param("lat", "37.5"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.message").value("좌표 값이 올바르지 않습니다."));
        kakao.verify();
    }

    @Test
    @DisplayName("로그인하지 않으면 401이고 카카오를 부르지 않는다")
    void unauthorized() throws Exception {
        mockMvc.perform(get("/api/places/reverse-geocode").param("lat", "37.5299").param("lng", "126.9648"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("인증이 필요합니다."));
        kakao.verify();
    }
}
