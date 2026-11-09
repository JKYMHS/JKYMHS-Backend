package com.jkymhs.backend.domain.content.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.jkymhs.backend.domain.content.entity.ContentType;
import com.jkymhs.backend.domain.content.entity.IoType;
import com.jkymhs.backend.domain.content.service.FestivalStatus;
import com.jkymhs.backend.domain.content.service.OpenStatus;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * 장소·축제 상세.
 *
 * @param playtime        축제 공연 시간 원문
 * @param operatingHours  운영 시간 원문 (형식이 제각각이라 그대로 보여준다)
 * @param restDateRaw     쉬는 날 원문
 * @param detailAvailable 소개글·사진 등 상세를 한 번이라도 받아 둔 적이 있는지. false면 FE는 기본 정보만 보여주고 나머지 영역에 재시도 안내를 띄운다
 * @param isStale         저장된 상세가 오래됐는데 이번에 새로 받지 못했다 (TourAPI 실패 등)
 */
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
