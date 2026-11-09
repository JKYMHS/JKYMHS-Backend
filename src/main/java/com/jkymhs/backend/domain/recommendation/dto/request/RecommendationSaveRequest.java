package com.jkymhs.backend.domain.recommendation.dto.request;

import com.jkymhs.backend.domain.recommendation.entity.MealType;
import com.jkymhs.backend.domain.trip.dto.request.OriginRequest;
import com.jkymhs.backend.global.model.Companion;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/**
 * 추천 코스를 여행/일정안으로 저장한다 (004-07).
 *
 * @param target            기존 여행 {tripId} 또는 새 여행 {newTrip} 중 정확히 하나
 * @param planName          일정안 이름. 없으면 "AI 추천 · MM.DD"(추천 가는 날)
 * @param makeConfirmed     기존 여행에 넣을 때 이 안을 확정안으로 바꿀지 (새 여행은 항상 확정)
 * @param mealSelections    식사 칸에 넣을 음식점. 고르지 않은 식사 칸은 저장하지 않는다
 * @param removedContentIds 사용자가 코스에서 뺀 장소 (저장하지 않고, 취향 신호로 쓴다)
 */
public record RecommendationSaveRequest(
        @NotBlank(message = "저장할 코스를 선택해주세요.") String courseId,
        @NotNull(message = "저장할 여행을 선택해주세요.") @Valid Target target,
        @Size(min = 1, max = 30, message = "일정안 이름은 1~30자로 입력해주세요.") String planName,
        Boolean makeConfirmed,
        List<@Valid @NotNull(message = "식사 선택 값이 올바르지 않습니다.") MealSelection> mealSelections,
        List<Long> removedContentIds
) {

    public boolean confirm() {
        return Boolean.TRUE.equals(makeConfirmed);
    }

    public List<MealSelection> meals() {
        return mealSelections == null ? List.of() : mealSelections;
    }

    public List<Long> removedIds() {
        return removedContentIds == null ? List.of() : removedContentIds.stream().filter(id -> id != null).toList();
    }

    public record Target(Long tripId, @Valid NewTrip newTrip) {
    }

    /**
     * 비운 값은 추천 조건으로 채운다.
     */
    public record NewTrip(
            @Size(min = 1, max = 30, message = "여행 이름은 30자까지 입력할 수 있습니다.") String title,
            LocalDate startDate,
            LocalDate endDate,
            Companion companion,
            @Size(min = 1, max = 5, message = "지역은 최대 5개까지 선택할 수 있습니다.") List<Long> regionIds,
            @Valid OriginRequest origin
    ) {
    }

    public record MealSelection(
            @NotNull(message = "식사 날짜를 선택해주세요.") Integer dayNumber,
            @NotNull(message = "식사 종류를 선택해주세요.") MealType mealType,
            @NotNull(message = "음식점을 선택해주세요.") Long contentId
    ) {
    }
}
