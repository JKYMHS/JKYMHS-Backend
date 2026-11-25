package com.jkymhs.backend.domain.recommendation.service;

import com.jkymhs.backend.domain.recommendation.entity.MealType;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public final class CourseTimeline {
    private static final int EARLY_MEAL_MARGIN_MINUTES = 60;

    private CourseTimeline() {
    }

    public sealed interface Slot permits PlaceSlot, MealSlot {
        int start();

        int end();
    }

    public record PlaceSlot(int index, int start, int end) implements Slot {
    }

    public record MealSlot(MealType mealType, int start, int end) implements Slot {
    }

    public static List<Slot> layout(LocalTime dayStart, List<Integer> stayMinutes, List<Integer> moveMinutes) {
        List<Slot> slots = new ArrayList<>();
        Set<MealType> done = EnumSet.noneOf(MealType.class);
        int cursor = dayStart.getHour() * 60 + dayStart.getMinute();
        for (int i = 0; i < stayMinutes.size(); i++) {
            int move = moveMinutes.get(i);
            int stay = stayMinutes.get(i);
            if (i > 0) {
                cursor = meals(cursor, move + stay, slots, done);
            }
            cursor += move;
            slots.add(new PlaceSlot(i, cursor, cursor + stay));
            cursor += stay;
        }
        if (!stayMinutes.isEmpty()) {
            for (MealType type : MealType.values()) {
                if (!done.contains(type) && inWindow(cursor, type)) {
                    slots.add(new MealSlot(type, cursor, cursor + MealType.MINUTES));
                    cursor += MealType.MINUTES;
                    done.add(type);
                }
            }
        }
        return slots;
    }

    public static LocalTime toTime(int minutes) {
        int clamped = Math.min(Math.max(minutes, 0), 23 * 60 + 59);
        return LocalTime.of(clamped / 60, clamped % 60);
    }

    public static int toMinutes(LocalTime time) {
        return time.getHour() * 60 + time.getMinute();
    }

    private static int meals(int cursor, int next, List<Slot> slots, Set<MealType> done) {
        for (MealType type : MealType.values()) {
            if (done.contains(type)) {
                continue;
            }
            int windowStart = toMinutes(type.windowStart());
            int latest = toMinutes(type.latestStart());
            if (inWindow(cursor, type)) {
                slots.add(new MealSlot(type, cursor, cursor + MealType.MINUTES));
                cursor += MealType.MINUTES;
                done.add(type);
            } else if (cursor < windowStart && cursor >= windowStart - EARLY_MEAL_MARGIN_MINUTES
                    && cursor + next > latest) {
                slots.add(new MealSlot(type, windowStart, windowStart + MealType.MINUTES));
                cursor = windowStart + MealType.MINUTES;
                done.add(type);
            }
        }
        return cursor;
    }

    private static boolean inWindow(int cursor, MealType type) {
        return cursor >= toMinutes(type.windowStart()) && cursor <= toMinutes(type.latestStart());
    }
}
