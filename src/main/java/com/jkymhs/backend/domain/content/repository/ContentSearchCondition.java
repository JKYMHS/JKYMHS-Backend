package com.jkymhs.backend.domain.content.repository;

import com.jkymhs.backend.domain.content.entity.ContentType;
import com.jkymhs.backend.domain.content.service.FestivalStatus;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;

public record ContentSearchCondition(
        ContentType contentType,
        String keyword,
        Long sidoRegionId,
        Long regionId,
        Long categoryId,
        Set<FestivalStatus> statuses,
        LocalDate today,
        LocalDate overlapFrom,
        LocalDate overlapTo,
        Integer minPopularity,
        DayOfWeek openDay,
        LocalTime openAt,
        Set<String> lclsCodes,
        boolean night,
        Bbox bbox
) {

    public record Bbox(double swLat, double swLng, double neLat, double neLng) {
    }
}
