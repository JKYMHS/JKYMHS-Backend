package com.jkymhs.backend.domain.content.service;

import com.jkymhs.backend.domain.content.entity.ContentType;
import com.jkymhs.backend.domain.content.entity.IoType;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;

/**
 * 다른 도메인(탐색, 찜, 여행, AI 추천)에 넘기는 콘텐츠 정보. 엔티티 대신 이 record를 넘겨서
 * 다른 도메인이 contents 테이블 구조나 지연 로딩에 묶이지 않게 한다.
 *
 * @param regionName   시·도 화면 이름 ("서울")
 * @param districtName 시·군·구 화면 이름 ("중구"). 시·군·구가 없으면 null
 * @param categoryName 소분류(lcls3) 이름 ("박물관")
 * @param restDays     빈 집합 = 쉬는 날 없음, null = 모름
 * @param petAllowed   null = 모름
 * @param deleted      TourAPI에서 내려간 콘텐츠 (찜·일정에는 "삭제된 장소"로 보여준다)
 */
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
