package com.jkymhs.backend.domain.content;

import com.jkymhs.backend.domain.content.service.ContentSource;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 탐색 테스트용 콘텐츠 값 바꾸기 ({@link ContentFixtures}의 기본 좌표는 모두 서울시청 37.5665, 126.9780이다).
 */
public final class ExploreFixtures {

    /** 서울시청 위도·경도 */
    public static final double BASE_LAT = 37.5665;
    public static final double BASE_LNG = 126.9780;
    /** 위도 1도 ≈ 111,195m (MySQL ST_Distance_Sphere의 지구 반지름 6,370,986m 기준) */
    public static final double METERS_PER_LAT_DEGREE = 111_195d;

    private ExploreFixtures() {
    }

    public static ContentSource at(ContentSource s, double lat, double lng) {
        return new ContentSource(s.id(), s.contentType(), s.tourContentTypeId(), s.name(), s.address(),
                BigDecimal.valueOf(lat).setScale(7, RoundingMode.HALF_UP),
                BigDecimal.valueOf(lng).setScale(7, RoundingMode.HALF_UP),
                s.ldongRegnCd(), s.ldongSignguCd(), s.lcls1(), s.lcls2(), s.lcls3(), s.thumbnailUrl(), s.tel(),
                s.eventStartDate(), s.eventEndDate(), s.tourModifiedAt());
    }

    /** 서울시청에서 정북쪽으로 meters만큼 떨어진 곳 */
    public static ContentSource northOfBase(ContentSource s, double meters) {
        return at(s, BASE_LAT + meters / METERS_PER_LAT_DEGREE, BASE_LNG);
    }
}
