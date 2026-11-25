package com.jkymhs.backend.domain.user.dto.response;

import com.jkymhs.backend.domain.user.entity.TravelStyle;

import java.util.Arrays;
import java.util.List;

public record TravelStyleListResponse(List<Item> styles) {

    public static TravelStyleListResponse all() {
        return new TravelStyleListResponse(Arrays.stream(TravelStyle.values()).map(Item::from).toList());
    }

    public record Item(String id, String name, String description) {

        static Item from(TravelStyle style) {
            return new Item(style.code(), style.getLabel(), style.getDescription());
        }
    }
}
