package com.jkymhs.backend.domain.content.service;

import com.jkymhs.backend.domain.content.entity.ContentType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 적재할 콘텐츠 한 건 (contents.csv 한 줄 또는 TourAPI 목록 항목 하나). 지역·카테고리는 아직 코드 상태이고,
 * {@link ContentService#upsert}가 {@link ContentLoadContext}로 id를 찾아 넣는다.
 */
public record ContentSource(
        Long id,
        ContentType contentType,
        int tourContentTypeId,
        String name,
        String address,
        BigDecimal latitude,
        BigDecimal longitude,
        String ldongRegnCd,
        String ldongSignguCd,
        String lcls1,
        String lcls2,
        String lcls3,
        String thumbnailUrl,
        String tel,
        LocalDate eventStartDate,
        LocalDate eventEndDate,
        LocalDateTime tourModifiedAt
) {

    /**
     * 기능 명세서 006-01: 좌표가 없으면 지도·경로에 쓸 수 없고, 축제는 기간이 없으면 진행 상태를 정할 수 없어서 적재하지 않는다.
     *
     * @return 적재하지 않는 이유. 적재해도 되면 null
     */
    public String skipReason() {
        if (id == null || contentType == null || name == null || name.isBlank()) {
            return "id·종류·이름 없음";
        }
        if (latitude == null || longitude == null || latitude.signum() == 0 || longitude.signum() == 0) {
            return "좌표 없음";
        }
        if (contentType == ContentType.FESTIVAL && (eventStartDate == null || eventEndDate == null)) {
            return "축제 날짜 없음";
        }
        return null;
    }

    public ContentSource withEventDates(LocalDate start, LocalDate end) {
        return new ContentSource(id, contentType, tourContentTypeId, name, address, latitude, longitude, ldongRegnCd,
                ldongSignguCd, lcls1, lcls2, lcls3, thumbnailUrl, tel, start, end, tourModifiedAt);
    }
}
