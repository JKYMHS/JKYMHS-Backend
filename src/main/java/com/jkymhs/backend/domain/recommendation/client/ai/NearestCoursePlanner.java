package com.jkymhs.backend.domain.recommendation.client.ai;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

public final class NearestCoursePlanner {
    public static final int DEFAULT_STAY_MINUTES = 90;

    private NearestCoursePlanner() {
    }

    public static AiCourseResponse plan(AiCourseRequest request, Function<Integer, String> titleOf,
                                        Function<AiCourseRequest.Candidate, String> reasonOf) {
        int days = request.conditions().days();
        int perDay = request.rules().placesPerDay().max();
        List<AiCourseRequest.Candidate> required = request.candidates().stream()
                .filter(AiCourseRequest.Candidate::required).toList();
        List<AiCourseRequest.Candidate> others = request.candidates().stream().filter(c -> !c.required()).toList();
        int wanted = Math.max(0, days * perDay - required.size());

        List<AiCourseResponse.Course> courses = new ArrayList<>();
        for (int n = 0; n < request.rules().courses(); n++) {
            int offset = n * perDay;
            if (offset + wanted > others.size()) {
                offset = 0;
            }
            Set<AiCourseRequest.Candidate> chosen = new LinkedHashSet<>(required);
            others.stream().skip(offset).limit(wanted).forEach(chosen::add);
            courses.add(new AiCourseResponse.Course(titleOf.apply(n), null,
                    split(request, new ArrayList<>(chosen), days, perDay, reasonOf)));
        }
        return new AiCourseResponse(courses);
    }

    private static List<AiCourseResponse.Day> split(AiCourseRequest request, List<AiCourseRequest.Candidate> chosen,
                                                    int days, int perDay,
                                                    Function<AiCourseRequest.Candidate, String> reasonOf) {
        List<AiCourseResponse.Day> result = new ArrayList<>();
        AiCourseRequest.Point origin = request.conditions().origin();
        double lat = origin == null ? 0 : origin.lat();
        double lng = origin == null ? 0 : origin.lng();
        List<AiCourseRequest.Candidate> remaining = new ArrayList<>(chosen);
        for (int day = 1; day <= days; day++) {
            var date = request.conditions().startDate().plusDays(day - 1);
            int slotsLeft = (days - day + 1) * perDay;
            List<AiCourseResponse.Item> items = new ArrayList<>();
            while (items.size() < perDay && !remaining.isEmpty()) {
                long requiredLeft = remaining.stream().filter(AiCourseRequest.Candidate::required).count();
                boolean onlyRequired = requiredLeft >= slotsLeft - items.size();
                AiCourseRequest.Candidate next = nearest(remaining, lat, lng,
                        c -> onlyRequired ? c.required() : c.required() || c.runsOn(date));
                if (next == null) {
                    break;
                }
                remaining.remove(next);
                items.add(new AiCourseResponse.Item(next.contentId(), DEFAULT_STAY_MINUTES, reasonOf.apply(next)));
                lat = next.lat();
                lng = next.lng();
            }
            result.add(new AiCourseResponse.Day(day, items));
        }
        return result;
    }

    private static AiCourseRequest.Candidate nearest(List<AiCourseRequest.Candidate> remaining, double lat, double lng,
                                                     java.util.function.Predicate<AiCourseRequest.Candidate> allowed) {
        AiCourseRequest.Candidate best = null;
        double bestDistance = Double.MAX_VALUE;
        for (AiCourseRequest.Candidate candidate : remaining) {
            if (!allowed.test(candidate)) {
                continue;
            }
            double dLat = candidate.lat() - lat;
            double dLng = (candidate.lng() - lng) * Math.cos(Math.toRadians(lat));
            double distance = dLat * dLat + dLng * dLng;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        return best;
    }
}
