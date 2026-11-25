package com.jkymhs.backend.domain.recommendation.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.jkymhs.backend.domain.trip.dto.request.OriginRequest;
import com.jkymhs.backend.global.model.ChildAgeGroup;
import com.jkymhs.backend.global.model.Companion;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

public record RecommendationCreateRequest(
        @NotNull(message = "지역을 선택해주세요.") Long regionId,
        List<Long> districtIds,
        @NotNull(message = "가는 날을 선택해주세요.") LocalDate startDate,
        @NotNull(message = "오는 날을 선택해주세요.") LocalDate endDate,
        @JsonFormat(pattern = "HH:mm") LocalTime startTime,
        @NotNull(message = "출발지를 입력해주세요.") @Valid OriginRequest origin,
        @NotNull(message = "누구와 가는지 선택해주세요.") Companion companion,
        ChildAgeGroup childAgeGroup,
        Boolean indoor,
        List<Long> requiredContentIds,
        Long targetTripId
) {
    public List<Long> districtIdList() {
        return distinct(districtIds);
    }

    public List<Long> requiredIdList() {
        return distinct(requiredContentIds);
    }

    public boolean indoorPreferred() {
        return Boolean.TRUE.equals(indoor);
    }

    private static List<Long> distinct(List<Long> ids) {
        if (ids == null) {
            return List.of();
        }
        return new ArrayList<>(new LinkedHashSet<>(ids.stream().filter(Objects::nonNull).toList()));
    }
}
