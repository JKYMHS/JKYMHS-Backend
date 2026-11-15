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

/**
 * 탐색 목록·비교 담기·(D3) 찜 목록이 같이 쓰는 카드 한 장. FE ContentItem(explore/model/types.ts)과 필드가 같다.
 *
 * 표시 문구("D-4", "~18:00", "화요일 휴무")는 만들지 않고 원값만 준다. 문구는 FE가 언어·디자인에 맞춰 만든다.
 * 축제 전용 값(status)과 장소 전용 값(openStatus)은 반대 종류에서 null이다.
 *
 * @param regionName   시·도 화면 이름 ("서울")
 * @param districtName 시·군·구 화면 이름 ("중구"). 없으면 null
 * @param category     소분류 이름 ("궁")
 * @param status       축제 진행 상태 (오늘 KST 기준)
 * @param openStatus   장소의 지금 영업 상태 (지금 KST 기준). 운영 시간을 모르면 null
 * @param restDays     ["MON", …], 연중무휴면 [], 모르면 null
 */
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

    /** 요일 순 FE 코드 목록. 빈 목록(연중무휴)과 null(모름)을 구분한다. */
    public static List<String> restDayCodes(ContentSnapshot s) {
        if (s.restDays() == null) {
            return null;
        }
        return s.restDays().stream().sorted().map(RestDays::shortName).toList();
    }
}
