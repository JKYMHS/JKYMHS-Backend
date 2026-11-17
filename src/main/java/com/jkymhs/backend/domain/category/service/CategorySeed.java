package com.jkymhs.backend.domain.category.service;

import com.jkymhs.backend.domain.category.entity.CategoryGroupType;

import java.util.List;

/**
 * 서비스 카테고리 초기값. FE 탐색 필터·홈 칩과 같은 코드(code)를 쓴다.
 * 분류 코드는 관광공사 신 분류(lclsSystm)이고 어느 단계든 된다 (HS = 역사관광 전체, VE07 = 박물관·전시관 중분류).
 * 음식점(FD)·숙박(AC)은 탐색 필터에 들어가지 않아서 어느 카테고리에도 넣지 않는다.
 */
final class CategorySeed {

    static final List<Item> ITEMS = List.of(
            new Item(CategoryGroupType.PLACE_FILTER, "history", "역사·문화재", List.of("HS")),
            new Item(CategoryGroupType.PLACE_FILTER, "nature", "자연·공원", List.of("NA", "VE03")),
            new Item(CategoryGroupType.PLACE_FILTER, "exhibit", "전시·박물관", List.of("VE07")),
            new Item(CategoryGroupType.PLACE_FILTER, "experience", "체험·액티비티", List.of("EX", "LS", "VE02", "VE10")),
            new Item(CategoryGroupType.PLACE_FILTER, "landmark", "랜드마크·도시",
                    List.of("VE01", "VE04", "VE05", "VE06", "VE08", "VE09", "VE11", "VE12")),
            new Item(CategoryGroupType.PLACE_FILTER, "shopping", "쇼핑·시장", List.of("SH")),
            new Item(CategoryGroupType.FESTIVAL_FILTER, "festival", "축제", List.of("EV01")),
            new Item(CategoryGroupType.FESTIVAL_FILTER, "performance", "공연", List.of("EV02")),
            new Item(CategoryGroupType.FESTIVAL_FILTER, "event", "행사·전시", List.of("EV03")),
            new Item(CategoryGroupType.CHIP, "palace_village", "고궁·마을",
                    List.of("HS010100", "HS010600", "VE040200", "EX030100")),
            new Item(CategoryGroupType.CHIP, "museum_exhibit", "박물관·전시", List.of("VE07", "EV030100")),
            new Item(CategoryGroupType.CHIP, "view_park", "전망·공원", List.of("VE010200", "VE03", "NA04")),
            new Item(CategoryGroupType.CHIP, "with_kids", "아이와 함께",
                    List.of("VE020100", "VE020300", "VE020400", "VE020500", "VE030300", "VE070100", "VE070500",
                            "EX01", "EX02", "EX03"))
    );

    private CategorySeed() {
    }

    record Item(CategoryGroupType groupType, String code, String name, List<String> lclsCodes) {
    }
}
