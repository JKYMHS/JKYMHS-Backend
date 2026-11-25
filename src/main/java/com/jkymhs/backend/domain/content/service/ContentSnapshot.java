package com.jkymhs.backend.domain.content.service;

import com.jkymhs.backend.domain.content.entity.ContentType;
import com.jkymhs.backend.domain.content.entity.IoType;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;

public record ContentSnapshot(
        Long id,
        ContentType contentType,
        String name,
        String address,
        double lat,
        double lng,
        String thumbnailUrl,
        Long sidoRegionId,
        Long regionId,
        String regionName,
        String districtName,
        String lcls1,
        String lcls2,
        String lcls3,
        String categoryName,
        IoType ioType,
        LocalDate eventStartDate,
        LocalDate eventEndDate,
        LocalTime eventStartTime,
        LocalTime eventEndTime,
        LocalTime openTime,
        LocalTime closeTime,
        LocalTime lastEntryTime,
        Set<DayOfWeek> restDays,
        Boolean petAllowed,
        int popularityScore,
        int wishCount,
        boolean deleted
) {
}
