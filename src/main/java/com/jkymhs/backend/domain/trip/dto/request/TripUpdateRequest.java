package com.jkymhs.backend.domain.trip.dto.request;

import com.jkymhs.backend.global.model.ChildAgeGroup;
import com.jkymhs.backend.global.model.Companion;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public record TripUpdateRequest(
        @Size(max = 30, message = "여행 이름은 30자까지 입력할 수 있습니다.")
        @Pattern(regexp = "(?s).*\\S.*", message = "여행 이름을 입력해주세요.") String title,
        LocalDate startDate,
        LocalDate endDate,
        @Size(min = 1, max = 5, message = "지역은 1~5개 선택할 수 있습니다.")
        List<@NotNull(message = "지역을 선택해주세요.") Long> regionIds,
        Companion companion,
        ChildAgeGroup childAgeGroup,
        @Valid OriginRequest origin,
        Boolean clearOrigin,
        Boolean removeOutOfRangeItems
) {
}
