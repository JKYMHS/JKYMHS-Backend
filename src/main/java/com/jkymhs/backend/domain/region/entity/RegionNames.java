package com.jkymhs.backend.domain.region.entity;

import java.util.Map;

/**
 * 시·도 공식 이름 → 화면용 짧은 이름. FE 지역 선택 화면과 카드에 "서울특별시" 대신 "서울"을 쓴다.
 *
 * 2026년 개편 이름(전남광주통합특별시, ○○특별자치도)과 개편 전 이름을 모두 둔다.
 * 법정동 코드 목록이 바뀌어 처음 보는 이름이 오면 행정구역 접미사를 떼서 쓴다 (예: "○○특별자치시" → "○○").
 * 시·군·구는 공식 이름이 이미 짧아서("중구", "해남군") 그대로 쓴다.
 */
public final class RegionNames {

    private static final Map<String, String> SIDO_SHORT_NAMES = Map.ofEntries(
            Map.entry("서울특별시", "서울"),
            Map.entry("부산광역시", "부산"),
            Map.entry("대구광역시", "대구"),
            Map.entry("인천광역시", "인천"),
            Map.entry("광주광역시", "광주"),
            Map.entry("대전광역시", "대전"),
            Map.entry("울산광역시", "울산"),
            Map.entry("세종특별자치시", "세종"),
            Map.entry("경기도", "경기"),
            Map.entry("강원도", "강원"),
            Map.entry("강원특별자치도", "강원"),
            Map.entry("충청북도", "충북"),
            Map.entry("충청남도", "충남"),
            Map.entry("전라북도", "전북"),
            Map.entry("전북특별자치도", "전북"),
            Map.entry("전라남도", "전남"),
            Map.entry("전남광주통합특별시", "전남광주"),
            Map.entry("경상북도", "경북"),
            Map.entry("경상남도", "경남"),
            Map.entry("제주특별자치도", "제주")
    );

    // 긴 접미사부터 확인해야 "특별자치도"가 "도"로만 잘리지 않는다
    private static final String[] SIDO_SUFFIXES = {"통합특별시", "특별자치시", "특별자치도", "특별시", "광역시", "도"};

    private RegionNames() {
    }

    public static String sidoShortName(String officialName) {
        String known = SIDO_SHORT_NAMES.get(officialName);
        if (known != null) {
            return known;
        }
        for (String suffix : SIDO_SUFFIXES) {
            if (officialName.endsWith(suffix) && officialName.length() > suffix.length()) {
                return officialName.substring(0, officialName.length() - suffix.length());
            }
        }
        return officialName;
    }
}
