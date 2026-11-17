package com.jkymhs.backend.domain.content;

import com.jkymhs.backend.domain.content.entity.ContentType;
import com.jkymhs.backend.domain.content.entity.IoType;
import com.jkymhs.backend.domain.content.service.ClassificationSource;
import com.jkymhs.backend.domain.content.service.ContentSource;
import com.jkymhs.backend.domain.region.service.RegionRef;
import com.jkymhs.backend.domain.region.service.RegionSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 콘텐츠 관련 테스트가 같이 쓰는 지역·분류·콘텐츠 값.
 * 지역: 서울(11) 종로구(110)·중구(140), 부산(26) 해운대구(350), 세종(36110, 시·군·구 없음).
 */
public final class ContentFixtures {

    public static final List<RegionSource> REGIONS = List.of(
            new RegionSource("11", "", "서울특별시"),
            new RegionSource("11", "140", "중구"),
            new RegionSource("11", "110", "종로구"),
            new RegionSource("26", "", "부산광역시"),
            new RegionSource("26", "350", "해운대구"),
            new RegionSource("36110", "", "세종특별자치시")
    );

    public static final List<ClassificationSource> CLASSIFICATIONS = List.of(
            new ClassificationSource("HS", 1, null, "역사관광", null),
            new ClassificationSource("HS01", 2, "HS", "역사유적지", null),
            new ClassificationSource("HS010100", 3, "HS01", "궁", IoType.OUTDOOR),
            new ClassificationSource("VE", 1, null, "문화관광", null),
            new ClassificationSource("VE07", 2, "VE", "박물관·전시관", null),
            new ClassificationSource("VE070100", 3, "VE07", "박물관", IoType.INDOOR),
            new ClassificationSource("EV", 1, null, "축제/공연/행사", null),
            new ClassificationSource("EV01", 2, "EV", "축제", null),
            new ClassificationSource("EV010100", 3, "EV01", "문화관광축제", IoType.OUTDOOR),
            new ClassificationSource("FD", 1, null, "음식", null),
            new ClassificationSource("FD01", 2, "FD", "한식", null),
            new ClassificationSource("FD010100", 3, "FD01", "한식", IoType.INDOOR)
    );

    private ContentFixtures() {
    }

    /** 서울 중구 궁 (역사·문화재) */
    public static ContentSource attraction(long id, String name, String regnCd, String signguCd) {
        return content(id, ContentType.ATTRACTION, name, regnCd, signguCd, "HS", "HS01", "HS010100", null, null);
    }

    /** 문화관광축제 (축제) */
    public static ContentSource festival(long id, String name, String regnCd, String signguCd, LocalDate start, LocalDate end) {
        return content(id, ContentType.FESTIVAL, name, regnCd, signguCd, "EV", "EV01", "EV010100", start, end);
    }

    public static ContentSource restaurant(long id, String name, String regnCd, String signguCd) {
        return content(id, ContentType.RESTAURANT, name, regnCd, signguCd, "FD", "FD01", "FD010100", null, null);
    }

    public static ContentSource content(long id, ContentType type, String name, String regnCd, String signguCd,
                                        String lcls1, String lcls2, String lcls3, LocalDate start, LocalDate end) {
        int tourTypeId = switch (type) {
            case ATTRACTION -> 12;
            case FESTIVAL -> 15;
            case RESTAURANT -> 39;
            case LODGING -> 32;
        };
        return new ContentSource(id, type, tourTypeId, name, "주소 " + name, new BigDecimal("37.5665000"),
                new BigDecimal("126.9780000"), regnCd, signguCd, lcls1, lcls2, lcls3,
                "https://img.test/" + id + ".jpg", "02-000-0000", start, end, LocalDateTime.of(2026, 11, 1, 9, 0));
    }

    /** 화면 이름(시·도 "서울", 시·군·구 "중구")으로 id를 찾는다 */
    public static Long regionId(List<RegionRef> regions, String fullName) {
        return regions.stream().filter(r -> r.fullName().equals(fullName)).findFirst().orElseThrow().id();
    }
}
