package com.jkymhs.backend.domain.user.dto.response;

import com.jkymhs.backend.domain.user.entity.TravelStyle;

import java.util.Arrays;
import java.util.List;

public record TravelStyleListResponse(List<Item> styles) {

    // enum 선언 순서 = 화면 표시 순서
    public static TravelStyleListResponse all() {
        return new TravelStyleListResponse(Arrays.stream(TravelStyle.values()).map(Item::from).toList());
    }

    /**
     * @param id 취향 저장 요청의 styleIds에 그대로 넣는 값 (소문자 코드)
     */
    public record Item(String id, String name, String description) {

        static Item from(TravelStyle style) {
            return new Item(style.code(), style.getLabel(), style.getDescription());
        }
    }
}
