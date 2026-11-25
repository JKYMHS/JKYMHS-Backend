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

public record CourseResponse(String courseId, String title, Summary summary, List<Day> days) {
    public record Summary(int places, int moveMinutes) {
    }

    public record Day(int dayNumber, LocalDate date, Origin origin, List<TimelineItem> items) {
    }

    public record Origin(String name, double lat, double lng) {
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = PlaceItem.class, name = "place"),
            @JsonSubTypes.Type(value = MoveItem.class, name = "move"),
            @JsonSubTypes.Type(value = MealItem.class, name = "meal")
    })
    public sealed interface TimelineItem permits PlaceItem, MoveItem, MealItem {
    }

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

    public record MealItem(
            MealType mealType,
            @JsonFormat(pattern = "HH:mm") LocalTime startTime,
            @JsonFormat(pattern = "HH:mm") LocalTime endTime,
            int nearbyRestaurantCount
    ) implements TimelineItem {
    }
}
