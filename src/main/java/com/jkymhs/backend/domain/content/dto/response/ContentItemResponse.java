package com.jkymhs.backend.domain.content.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.jkymhs.backend.domain.content.entity.ContentType;
import com.jkymhs.backend.domain.content.entity.IoType;
import com.jkymhs.backend.domain.content.service.ContentSnapshot;
import com.jkymhs.backend.domain.content.service.FestivalStatus;
import com.jkymhs.backend.domain.content.service.OpenStatus;
import com.jkymhs.backend.domain.content.service.OpenStatusCalculator;
import com.jkymhs.backend.domain.content.service.RestDays;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

public record ContentItemResponse(
        Long id,
        ContentType kind,
        String name,
        String regionName,
        String districtName,
        String address,
        IoType io,
        String category,
        String thumbnailUrl,
        double lat,
        double lng,
        boolean liked,
        LocalDate startDate,
        LocalDate endDate,
        FestivalStatus status,
        int popularity,
        OpenStatus openStatus,
        @JsonFormat(pattern = "HH:mm") LocalTime openTime,
        @JsonFormat(pattern = "HH:mm") LocalTime closeTime,
        List<String> restDays
) {

    public static ContentItemResponse of(ContentSnapshot s, boolean liked, LocalDateTime now, int soonMinutes) {
        boolean festival = s.contentType() == ContentType.FESTIVAL;
        return new ContentItemResponse(
                s.id(), s.contentType(), s.name(), s.regionName(), s.districtName(), s.address(), s.ioType(),
                s.categoryName(), s.thumbnailUrl(), s.lat(), s.lng(), liked,
                s.eventStartDate(), s.eventEndDate(),
                festival ? FestivalStatus.of(s.eventStartDate(), s.eventEndDate(), now.toLocalDate()) : null,
                s.popularityScore(),
                festival ? null : OpenStatusCalculator.of(s, now, soonMinutes),
                s.openTime(), s.closeTime(), restDayCodes(s));
    }

    public static List<String> restDayCodes(ContentSnapshot s) {
        if (s.restDays() == null) {
            return null;
        }
        return s.restDays().stream().sorted().map(RestDays::shortName).toList();
    }
}
