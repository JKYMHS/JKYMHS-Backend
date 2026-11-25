package com.jkymhs.backend.domain.content.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.jkymhs.backend.domain.content.entity.ContentType;
import com.jkymhs.backend.domain.content.entity.IoType;
import com.jkymhs.backend.domain.content.service.FestivalStatus;
import com.jkymhs.backend.domain.content.service.OpenStatus;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record ContentDetailResponse(
        Long id,
        ContentType kind,
        String name,
        String regionName,
        String districtName,
        String address,
        double lat,
        double lng,
        IoType io,
        String category,
        String thumbnailUrl,
        List<String> images,
        String overview,
        LocalDate startDate,
        LocalDate endDate,
        FestivalStatus status,
        String eventPlace,
        @JsonFormat(pattern = "HH:mm") LocalTime eventStartTime,
        @JsonFormat(pattern = "HH:mm") LocalTime eventEndTime,
        String playtime,
        String operatingHours,
        @JsonFormat(pattern = "HH:mm") LocalTime openTime,
        @JsonFormat(pattern = "HH:mm") LocalTime closeTime,
        @JsonFormat(pattern = "HH:mm") LocalTime lastEntryTime,
        List<String> restDays,
        String restDateRaw,
        OpenStatus openStatus,
        String fee,
        String homepage,
        String contact,
        String host,
        String ageLimit,
        String eventProgram,
        Boolean petAllowed,
        int popularity,
        int wishCount,
        boolean liked,
        boolean detailAvailable,
        boolean isStale
) {
}
