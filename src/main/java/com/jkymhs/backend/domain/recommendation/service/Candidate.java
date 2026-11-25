package com.jkymhs.backend.domain.recommendation.service;

import com.jkymhs.backend.domain.content.entity.ContentType;
import com.jkymhs.backend.domain.content.service.ContentSnapshot;

import java.time.LocalDate;

public record Candidate(ContentSnapshot content, double score, boolean required) {
    public Long id() {
        return content.id();
    }

    public boolean isFestival() {
        return content.contentType() == ContentType.FESTIVAL;
    }

    public boolean runsOn(LocalDate date) {
        if (!isFestival() || content.eventStartDate() == null || content.eventEndDate() == null) {
            return true;
        }
        return !date.isBefore(content.eventStartDate()) && !date.isAfter(content.eventEndDate());
    }
}
