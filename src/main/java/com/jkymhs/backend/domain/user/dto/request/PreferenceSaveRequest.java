package com.jkymhs.backend.domain.user.dto.request;

import com.jkymhs.backend.domain.user.entity.Pace;
import com.jkymhs.backend.domain.user.entity.TimeOfDay;
import com.jkymhs.backend.domain.user.entity.TravelStyle;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

public record PreferenceSaveRequest(
        @NotNull(message = "여행 스타일을 1~3개 선택해주세요.")
        @Size(min = 1, max = 3, message = "여행 스타일을 1~3개 선택해주세요.")
        List<TravelStyle> styleIds,

        @NotNull(message = "여행 속도를 선택해주세요.")
        Pace pace,

        @NotNull(message = "여행 시작 시간대를 선택해주세요.")
        TimeOfDay timeOfDay,

        @NotNull(message = "이동 편의 여부를 입력해주세요.")
        Boolean accessible,

        @NotNull(message = "반려동물 동반 여부를 입력해주세요.")
        Boolean pet
) {

    @AssertTrue(message = "여행 스타일을 1~3개 선택해주세요.")
    public boolean isStyleIdsDistinct() {
        if (styleIds == null) {
            return true;
        }
        return styleIds.stream().allMatch(Objects::nonNull) && new HashSet<>(styleIds).size() == styleIds.size();
    }
}
