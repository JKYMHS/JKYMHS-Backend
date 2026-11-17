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

/**
 * 추천 요청 (FE RecommendDraft). 기간·꼭 포함 장소 수처럼 문구가 따로 정해진 검사는 서비스에서 한다.
 *
 * @param regionId           시·도 id
 * @param districtIds        그 시·도 아래 시·군·구 id들. 비어 있으면 시·도 전체
 * @param startTime          첫째 날 시작 시각. 없으면 취향의 시작 시간대
 * @param requiredContentIds 꼭 포함할 장소 (순서 있음, 최대 5개). 같은 id가 두 번 오면 한 번만 쓴다
 * @param targetTripId       저장할 때 기본으로 고를 여행 (내 여행이어야 한다)
 */
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
