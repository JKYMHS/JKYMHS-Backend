package com.jkymhs.backend.domain.trip.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.jkymhs.backend.domain.route.service.MoveMode;
import com.jkymhs.backend.domain.trip.entity.PlanSource;
import com.jkymhs.backend.domain.trip.service.CalendarState;
import com.jkymhs.backend.domain.trip.service.PlanWarning;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * 일정안 상세. 여행 기간의 모든 날을 days로 내려준다 (장소가 없는 날도 빈 items로).
 */
public record PlanDetailResponse(
        Long id,
        Long tripId,
        String name,
        PlanSource source,
        boolean isConfirmed,
        int version,
        int totalMoveMinutes,
        OriginResponse origin,
        List<Day> days
) {

    /**
     * @param warningCount 그날 항목들의 경고 수 합계
     */
    public record Day(int dayNumber, LocalDate date, int placeCount, int warningCount, List<Item> items) {
    }

    /**
     * @param contentDeleted TourAPI에서 내려간 장소 (저장해 둔 이름·사진으로 보여준다)
     * @param move           앞 장소(첫째 날 첫 장소는 출발지)에서 오는 이동. 그날 첫 장소면 null
     */
    public record Item(
            Long itemId,
            Long contentId,
            String name,
            String address,
            String imageUrl,
            String category,
            double lat,
            double lng,
            @JsonFormat(pattern = "HH:mm") LocalTime startTime,
            @JsonFormat(pattern = "HH:mm") LocalTime endTime,
            Integer stayMinutes,
            String memo,
            String aiReason,
            boolean isRequired,
            boolean contentDeleted,
            Move move,
            List<PlanWarning> warnings,
            CalendarState calendarState
    ) {
    }

    public record Move(
            MoveMode mode,
            Integer minutes,
            MoveMode altMode,
            Integer altMinutes,
            String summary,
            String polyline,
            boolean isEstimated
    ) {
    }
}
