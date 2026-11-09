package com.jkymhs.backend.domain.trip.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalTime;
import java.util.List;

/**
 * 일정안 전체 저장. 화면의 목록을 통째로 보낸다 (있는 itemId = 수정, null = 새 항목, 빠진 항목 = 삭제).
 * items 개수(1~30)는 FE가 code로 분기하는 SCHEDULE_LIMIT_EXCEEDED로 답해야 해서 애너테이션이 아니라 서비스에서 확인한다.
 *
 * @param version   상세 조회 때 받은 버전. 그사이 다른 기기에서 저장했으면 409
 * @param tripTitle 같은 화면에서 여행 이름도 바꿀 수 있다 (없으면 그대로)
 */
public record PlanSaveRequest(
        @NotNull(message = "버전을 입력해주세요.") Integer version,
        @NotBlank(message = "일정안 이름을 입력해주세요.")
        @Size(max = 30, message = "일정안 이름은 30자까지 입력할 수 있습니다.") String name,
        @Size(max = 30, message = "여행 이름은 30자까지 입력할 수 있습니다.")
        @Pattern(regexp = "(?s).*\\S.*", message = "여행 이름을 입력해주세요.") String tripTitle,
        List<@NotNull(message = "장소를 입력해주세요.") @Valid Item> items
) {

    public record Item(
            Long itemId,
            @NotNull(message = "장소를 입력해주세요.") Long contentId,
            @NotNull(message = "날짜를 입력해주세요.") Integer dayNumber,
            @NotNull(message = "순서를 입력해주세요.") Integer sortOrder,
            @JsonFormat(pattern = "HH:mm") LocalTime startTime,
            @JsonFormat(pattern = "HH:mm") LocalTime endTime,
            @Min(value = 0, message = "머무는 시간이 올바르지 않습니다.") Integer stayMinutes,
            @Size(max = 200, message = "메모는 200자까지 입력할 수 있습니다.") String memo
    ) {
    }
}
