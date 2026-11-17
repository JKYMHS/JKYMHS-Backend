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

/**
 * 취향 전체 저장 (PUT). 모든 값이 필수다. Boolean 래퍼를 써서 값이 빠진 것(null)을 false로 짐작하지 않고 400으로 돌려준다.
 * styleIds의 값은 FE 소문자 코드("rest")다. 모르는 코드는 JSON을 enum으로 바꾸다 실패해서 400 INVALID_INPUT(description "요청 형식이 올바르지 않습니다.")이 된다.
 */
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

    // ["rest", "rest"]처럼 같은 스타일을 두 번 보내거나 null이 섞이면 "1~3개 선택"을 어긴 것으로 본다
    @AssertTrue(message = "여행 스타일을 1~3개 선택해주세요.")
    public boolean isStyleIdsDistinct() {
        if (styleIds == null) {
            return true;
        }
        return styleIds.stream().allMatch(Objects::nonNull) && new HashSet<>(styleIds).size() == styleIds.size();
    }
}
