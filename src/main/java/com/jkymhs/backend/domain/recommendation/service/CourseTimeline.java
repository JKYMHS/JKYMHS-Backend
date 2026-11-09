package com.jkymhs.backend.domain.recommendation.service;

import com.jkymhs.backend.domain.recommendation.entity.MealType;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * 하루 일정의 시각을 매긴다 (작업 5단계 MEALS, 그리고 검증 단계의 임시 시각). 시각은 0시부터의 분(int)으로 계산해서
 * 자정을 넘겨도 값이 돌아가지 않게 한다 (예: 25:00 = 1500). 화면에 낼 때만 {@link #toTime}으로 바꾼다.
 *
 * 규칙:
 * <ul>
 *   <li>그날 시작 시각에서 출발해 "이동 → 머묾"을 차례로 더한다.</li>
 *   <li>식사 칸(60분)은 장소와 장소 사이에 넣는다. 앞 장소가 끝난 시각이 식사 창의 시작 가능 구간
 *       (점심 11:30~12:30, 저녁 17:30~18:30) 안이면 바로 넣는다.</li>
 *   <li>아직 창이 열리기 전(창 시작 1시간 전 이내)인데 다음 장소까지 다녀오면 시작 가능 구간을 넘겨 버리면,
 *       창이 열리는 시각(11:30/17:30)에 먼저 식사 칸을 넣는다 (점심을 건너뛰지 않게).</li>
 *   <li>마지막 장소가 끝난 시각이 시작 가능 구간 안이면 그날 끝에 넣는다. 그 전에 하루가 끝나면 넣지 않는다.</li>
 * </ul>
 */
public final class CourseTimeline {

    private static final int EARLY_MEAL_MARGIN_MINUTES = 60;

    private CourseTimeline() {
    }

    public sealed interface Slot permits PlaceSlot, MealSlot {
        int start();

        int end();
    }

    /** @param index 그날 장소 목록에서의 순서 (0부터) */
    public record PlaceSlot(int index, int start, int end) implements Slot {
    }

    public record MealSlot(MealType mealType, int start, int end) implements Slot {
    }

    /**
     * @param dayStart    그날 시작 시각
     * @param stayMinutes 장소별 머무는 시간
     * @param moveMinutes 장소별 "들어오는" 이동 시간 (출발점 → 첫 장소 포함). stayMinutes와 길이가 같다
     */
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
