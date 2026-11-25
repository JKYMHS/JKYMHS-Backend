package com.jkymhs.backend.domain.region.entity;

import java.util.Map;

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
