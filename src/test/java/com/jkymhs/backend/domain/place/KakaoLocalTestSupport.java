package com.jkymhs.backend.domain.place;

import com.jkymhs.backend.domain.place.client.KakaoLocalClient;
import com.jkymhs.backend.domain.place.client.KakaoLocalProperties;
import com.jkymhs.backend.domain.place.service.PlaceSearchService;
import com.jkymhs.backend.support.IntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * 카카오 로컬 API를 MockRestServiceServer로 흉내 낸다. 출발지 검색 서비스 빈의 클라이언트를 테스트용으로 바꿔 끼우고
 * 끝나면 되돌린다 (실제 카카오는 절대 부르지 않는다).
 */
abstract class KakaoLocalTestSupport extends IntegrationTestSupport {

    static final String BASE_URL = "https://kakao.test";
    static final String API_KEY = "test-kakao-key";

    @Autowired
    private PlaceSearchService placeSearchService;

    protected MockRestServiceServer kakao;
    private Object originalClient;

    @BeforeEach
    void bindKakaoMock() {
        useClient(API_KEY);
    }

    @AfterEach
    void restoreKakaoClient() {
        ReflectionTestUtils.setField(target(), "kakaoLocalClient", originalClient);
    }

    /** 키를 바꿔서 클라이언트를 다시 만든다 (빈 키 테스트용) */
    protected void useClient(String apiKey) {
        RestClient.Builder builder = RestClient.builder();
        kakao = MockRestServiceServer.bindTo(builder).build();
        if (originalClient == null) {
            originalClient = ReflectionTestUtils.getField(target(), "kakaoLocalClient");
        }
        KakaoLocalProperties properties = new KakaoLocalProperties(BASE_URL, apiKey, Duration.ofSeconds(1));
        ReflectionTestUtils.setField(target(), "kakaoLocalClient", new KakaoLocalClient(builder, properties));
    }

    private PlaceSearchService target() {
        return AopTestUtils.getUltimateTargetObject(placeSearchService);
    }
}
