package com.jkymhs.backend.domain.trip.dto.request;

import com.jkymhs.backend.domain.trip.service.NewTripCommand;
import com.jkymhs.backend.global.model.ChildAgeGroup;
import com.jkymhs.backend.global.model.Companion;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/**
 * 새 여행. 날짜 순서·기간·아이 나이 같은 필드끼리의 규칙은 서비스에서 확인한다 (상황별 문구가 다르다).
 */
public record TripCreateRequest(
        @NotBlank(message = "여행 이름을 입력해주세요.")
        @Size(max = 30, message = "여행 이름은 30자까지 입력할 수 있습니다.") String title,
        @NotNull(message = "가는 날을 선택해주세요.") LocalDate startDate,
        @NotNull(message = "오는 날을 선택해주세요.") LocalDate endDate,
        @NotEmpty(message = "지역을 선택해주세요.")
        @Size(max = 5, message = "지역은 최대 5개까지 선택할 수 있습니다.")
        List<@NotNull(message = "지역을 선택해주세요.") Long> regionIds,
        @NotNull(message = "누구와 가는지 선택해주세요.") Companion companion,
        ChildAgeGroup childAgeGroup,
        @Valid OriginRequest origin
) {

    public NewTripCommand toCommand() {
        return new NewTripCommand(title.trim(), startDate, endDate, regionIds, companion, childAgeGroup,
                origin == null ? null : origin.toOrigin());
    }
}
