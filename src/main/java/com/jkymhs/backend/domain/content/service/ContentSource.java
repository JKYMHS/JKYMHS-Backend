package com.jkymhs.backend.domain.content.service;

import com.jkymhs.backend.domain.content.entity.ContentType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

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
