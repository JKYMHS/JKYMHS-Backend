package com.jkymhs.backend.domain.recommendation.service;

import com.jkymhs.backend.domain.content.entity.ContentType;
import com.jkymhs.backend.domain.content.service.ContentSnapshot;

import java.time.LocalDate;

/**
 * 점수를 매긴 추천 후보.
 *
 * @param required 꼭 포함할 장소 (점수와 관계없이 들어가고, 영업시간·축제 기간 검사에서 빠진다)
 */
public record Candidate(ContentSnapshot content, double score, boolean required) {

    public Long id() {
        return content.id();
    }

    public boolean isFestival() {
        return content.contentType() == ContentType.FESTIVAL;
    }

    /** 축제면 그날 열리는지. 축제가 아니면 항상 true */
    public boolean runsOn(LocalDate date) {
        if (!isFestival() || content.eventStartDate() == null || content.eventEndDate() == null) {
            return true;
        }
        return !date.isBefore(content.eventStartDate()) && !date.isAfter(content.eventEndDate());
    }
}
