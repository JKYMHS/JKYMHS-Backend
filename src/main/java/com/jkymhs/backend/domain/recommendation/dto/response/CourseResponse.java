package com.jkymhs.backend.domain.recommendation.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.jkymhs.backend.domain.content.entity.IoType;
import com.jkymhs.backend.domain.recommendation.entity.MealType;
import com.jkymhs.backend.domain.route.service.MoveMode;
import com.jkymhs.backend.domain.route.service.RouteResult;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * 추천 코스 하나. Redis(rec:result)에도 이 모양 그대로 JSON으로 저장하고, 결과 API와 "저장" API가 같이 읽는다.
 *
 * @param courseId "c1" | "c2" | "c3"
 */
public record CourseResponse(String courseId, String title, Summary summary, List<Day> days) {

    /**
     * @param places      장소 수 (식사 칸 제외)
     * @param moveMinutes 이동 시간 합계
     */
    public record Summary(int places, int moveMinutes) {
    }

    /**
     * @param origin 그날 출발점. 첫날은 요청 출발지, 둘째 날부터는 전날 마지막 장소
     */
    public record Day(int dayNumber, LocalDate date, Origin origin, List<TimelineItem> items) {
    }

    public record Origin(String name, double lat, double lng) {
    }

    /**
     * 타임라인 칸 세 종류. JSON에는 {@code type}(place/move/meal)이 붙어서 FE와 저장 API가 종류를 구분한다.
     */
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = PlaceItem.class, name = "place"),
            @JsonSubTypes.Type(value = MoveItem.class, name = "move"),
            @JsonSubTypes.Type(value = MealItem.class, name = "meal")
    })
    public sealed interface TimelineItem permits PlaceItem, MoveItem, MealItem {
    }

    /**
     * @param category 소분류 이름 ("박물관")
     * @param reason   추천 이유
     */
    public record PlaceItem(
            Long contentId,
            String name,
            String category,
            IoType io,
            String thumbnailUrl,
            double lat,
            double lng,
            @JsonFormat(pattern = "HH:mm") LocalTime startTime,
            @JsonFormat(pattern = "HH:mm") LocalTime endTime,
            int stayMinutes,
            String reason,
            boolean isRequired
    ) implements TimelineItem {
    }

    /**
     * 앞 칸(첫날은 출발지)에서 다음 장소까지의 이동. distanceMeters는 저장할 때 일정 항목에 그대로 옮기려고 둔다.
     */
    public record MoveItem(
            MoveMode mode,
            int minutes,
            MoveMode altMode,
            Integer altMinutes,
            String summary,
            String polyline,
            boolean isEstimated,
            int distanceMeters
    ) implements TimelineItem {

        public static MoveItem from(RouteResult route) {
            return new MoveItem(route.mode(), route.minutes(), route.altMode(), route.altMinutes(), route.summary(),
                    route.polyline(), route.estimated(), route.distanceMeters());
        }

        public RouteResult toRoute() {
            return new RouteResult(mode, minutes, altMode, altMinutes, summary, polyline, isEstimated, distanceMeters);
        }
    }

    /**
     * @param nearbyRestaurantCount 앞 장소 1km 안의 음식점 수. FE는 이 칸을 누르면 주변 음식점 API로 목록을 받는다
     */
    public record MealItem(
            MealType mealType,
            @JsonFormat(pattern = "HH:mm") LocalTime startTime,
            @JsonFormat(pattern = "HH:mm") LocalTime endTime,
            int nearbyRestaurantCount
    ) implements TimelineItem {
    }
}
