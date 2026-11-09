package com.jkymhs.backend.domain.recommendation.service;

import com.jkymhs.backend.domain.behavior.service.PreferenceProfileService;
import com.jkymhs.backend.domain.behavior.service.ProfileView;
import com.jkymhs.backend.domain.content.entity.IoType;
import com.jkymhs.backend.domain.content.service.ContentSnapshot;
import com.jkymhs.backend.domain.recommendation.client.ai.AiCourseClient;
import com.jkymhs.backend.domain.recommendation.client.ai.AiCourseException;
import com.jkymhs.backend.domain.recommendation.client.ai.AiCourseRequest;
import com.jkymhs.backend.domain.recommendation.client.ai.AiCourseResponse;
import com.jkymhs.backend.domain.recommendation.dto.response.CourseResponse;
import com.jkymhs.backend.domain.recommendation.entity.FailureReason;
import com.jkymhs.backend.domain.recommendation.entity.MealType;
import com.jkymhs.backend.domain.recommendation.entity.RecommendationRequest;
import com.jkymhs.backend.domain.recommendation.entity.RecommendationRequestRegion;
import com.jkymhs.backend.domain.recommendation.entity.RecommendationRequiredPlace;
import com.jkymhs.backend.domain.recommendation.entity.RecommendationStatus;
import com.jkymhs.backend.domain.recommendation.entity.RecommendationStep;
import com.jkymhs.backend.domain.recommendation.repository.RecommendationRequestRegionRepository;
import com.jkymhs.backend.domain.recommendation.repository.RecommendationRequestRepository;
import com.jkymhs.backend.domain.recommendation.repository.RecommendationRequiredPlaceRepository;
import com.jkymhs.backend.domain.route.service.LatLng;
import com.jkymhs.backend.domain.user.entity.Pace;
import com.jkymhs.backend.domain.user.service.EffectivePreference;
import com.jkymhs.backend.domain.user.service.PreferenceService;
import com.jkymhs.backend.global.model.Companion;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 추천 작업 한 건 (recommendationExecutor 스레드에서 돈다). 단계마다 Redis 진행 상황을 고쳐서 FE polling이 보여준다.
 *
 * <ol>
 *   <li>CANDIDATES: 후보 고르기 ({@link CandidateService}). 하루 2곳도 안 되면 FAILED(NOT_ENOUGH_CANDIDATES)</li>
 *   <li>COMPOSE: AI에 코스 3개 요청</li>
 *   <li>HOURS: {@link CourseValidator}로 확인 → 어긴 규칙이 있으면 그 목록을 붙여 한 번 더 요청. 또 어기거나 AI 오류·시간 초과면
 *       {@link FallbackCourseBuilder}의 기본 코스 (FALLBACK)</li>
 *   <li>ROUTES·MEALS: 이동·시각·식사 칸 ({@link CourseAssembler})</li>
 *   <li>결과를 Redis에 저장하고 상태를 DONE/FALLBACK으로 바꾼다</li>
 * </ol>
 * 어느 단계든 예상하지 못한 예외는 FAILED(INTERNAL)로 끝낸다. FAILED는 하루 횟수에서 빠지므로 따로 돌려줄 것이 없다.
 * 전체 제한 시간(25초)이 지나면 AI 재요청과 외부 경로 API를 건너뛰어 FE의 30초 대기 안에 끝나게 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RecommendationWorker {

    static final int COURSES = 3;
    static final int MIN_CANDIDATES_PER_DAY = 2;
    private static final int TOP_PROFILE_CATEGORIES = 5;

    private final RecommendationRequestRepository requestRepository;
    private final RecommendationRequestRegionRepository regionRepository;
    private final RecommendationRequiredPlaceRepository requiredPlaceRepository;
    private final CandidateService candidateService;
    private final CourseValidator courseValidator;
    private final FallbackCourseBuilder fallbackCourseBuilder;
    private final CourseAssembler courseAssembler;
    private final RecommendationResultStore resultStore;
    // final이 아닌 이유: 테스트가 AI 응답을 바꿔 끼운다 (ReflectionTestUtils, RouteService의 외부 클라이언트와 같은 방식).
    // @NonNull이 붙은 필드는 @RequiredArgsConstructor 생성자에 들어간다
    @NonNull
    private AiCourseClient aiCourseClient;
    private final PreferenceService preferenceService;
    private final PreferenceProfileService preferenceProfileService;
    private final RecommendationProperties properties;
    private final Clock clock;

    public void run(Long requestId) {
        Instant deadline = clock.instant().plus(properties.deadline());
        try {
            RecommendationRequest request = requestRepository.findById(requestId).orElse(null);
            if (request == null || request.getStatus() != RecommendationStatus.PENDING) {
                return;
            }
            process(request, deadline);
        } catch (RuntimeException e) {
            log.error("추천 작업 실패: requestId={}", requestId, e);
            requestRepository.finish(requestId, RecommendationStatus.FAILED, FailureReason.INTERNAL);
        }
    }

    private void process(RecommendationRequest request, Instant deadline) {
        Long id = request.getId();
        resultStore.saveProgress(id, RecommendationStep.CANDIDATES, 0, 0);
        List<Long> districtIds = regionRepository.findAllByRequestIdOrderByIdAsc(id).stream()
                .map(RecommendationRequestRegion::getRegionId).toList();
        List<Long> requiredIds = requiredPlaceRepository.findAllByRequestIdOrderBySortOrderAsc(id).stream()
                .map(RecommendationRequiredPlace::getContentId).toList();
        EffectivePreference preference = preferenceService.getEffective(request.getUserId());

        List<Candidate> candidates = candidateService.rank(new CandidateQuery(request.getUserId(),
                request.getSidoRegionId(), districtIds, request.getStartDate(), request.getEndDate(),
                request.getCompanion(), request.isIndoorPreferred(), preference.styles(), preference.pet(), requiredIds,
                penalized(request)));
        resultStore.saveProgress(id, RecommendationStep.CANDIDATES, candidates.size(), 0);
        if (candidates.size() < MIN_CANDIDATES_PER_DAY * request.days()) {
            requestRepository.finish(id, RecommendationStatus.FAILED, FailureReason.NOT_ENOUGH_CANDIDATES);
            return;
        }

        int[] perDay = placesPerDay(preference.pace(), request.getCompanion());
        CourseContext context = new CourseContext(
                candidates.stream().collect(Collectors.toMap(Candidate::id, c -> c, (a, b) -> a, LinkedHashMap::new)),
                requiredIds, request.getStartDate(), request.days(), perDay[0], perDay[1],
                request.getCompanion() == Companion.KIDS, request.getStartTime(), preference.timeOfDay().startTime(),
                request.getOriginName(), new LatLng(request.getOriginLat().doubleValue(), request.getOriginLng().doubleValue()),
                COURSES);
        AiCourseRequest aiRequest = aiRequest(request, preference, candidates, context);

        resultStore.saveProgress(id, RecommendationStep.COMPOSE, candidates.size(), 0);
        AiCourseResponse response = compose(aiRequest);
        resultStore.saveProgress(id, RecommendationStep.HOURS, candidates.size(), 0);
        if (response != null) {
            List<String> violations = courseValidator.validate(response, context);
            if (!violations.isEmpty()) {
                log.info("AI 코스 규칙 위반 {}건, 다시 요청: requestId={}", violations.size(), id);
                response = clock.instant().isBefore(deadline) ? compose(aiRequest.withViolations(violations)) : null;
                if (response != null && !courseValidator.validate(response, context).isEmpty()) {
                    response = null;
                }
            }
        }
        boolean fallback = response == null;
        if (fallback) {
            response = fallbackCourseBuilder.build(aiRequest);
        }
        List<PlannedCourse> plans = courseValidator.toPlans(response, context);
        int selected = (int) plans.stream().flatMap(p -> p.days().stream()).flatMap(d -> d.places().stream())
                .map(p -> p.candidate().id()).distinct().count();

        resultStore.saveProgress(id, RecommendationStep.ROUTES, candidates.size(), selected);
        boolean allowExternal = clock.instant().isBefore(deadline);
        List<CourseResponse> courses = courseAssembler.assemble(plans, context, request.getCompanion().code(),
                allowExternal);
        resultStore.saveProgress(id, RecommendationStep.MEALS, candidates.size(), selected);

        RecommendationResult result = new RecommendationResult(courses,
                request.isIndoorPreferred() && indoorShortage(plans));
        try {
            resultStore.saveResult(id, result);
        } catch (DataAccessException e) {
            log.error("추천 결과 저장 실패: requestId={}", id, e);
            requestRepository.finish(id, RecommendationStatus.FAILED, FailureReason.INTERNAL);
            return;
        }
        requestRepository.finish(id, fallback ? RecommendationStatus.FALLBACK : RecommendationStatus.DONE, null);
    }

    private AiCourseResponse compose(AiCourseRequest aiRequest) {
        try {
            return aiCourseClient.compose(aiRequest);
        } catch (AiCourseException e) {
            log.warn("AI 코스 생성 실패 (기본 코스로 대신함): {}", e.getMessage());
            return null;
        }
    }

    // 다시 추천: 빼달라고 한 곳 + 직전 결과에 있던 곳
    private Set<Long> penalized(RecommendationRequest request) {
        Set<Long> ids = new HashSet<>(request.excludedIds());
        if (request.getPreviousRequestId() != null) {
            resultStore.findResultQuietly(request.getPreviousRequestId())
                    .ifPresent(previous -> ids.addAll(previous.contentIds()));
        }
        return ids;
    }

    /** [최소, 최대] 하루 장소 수: 여유롭게 2~3, 빡빡하게 4~5, 아이와 가면 하나씩 줄인다 */
    static int[] placesPerDay(Pace pace, Companion companion) {
        int min = pace == Pace.PACKED ? 4 : 2;
        int max = pace == Pace.PACKED ? 5 : 3;
        if (companion == Companion.KIDS) {
            min = Math.max(1, min - 1);
            max = max - 1;
        }
        return new int[]{min, max};
    }

    // 실내 장소가 절반이 안 되면 true (코스 3개 전체 기준)
    private static boolean indoorShortage(List<PlannedCourse> plans) {
        List<ContentSnapshot> places = plans.stream().flatMap(p -> p.days().stream())
                .flatMap(d -> d.places().stream()).map(p -> p.candidate().content()).toList();
        long indoor = places.stream().filter(c -> c.ioType() == IoType.INDOOR).count();
        return indoor * 2 < places.size();
    }

    private AiCourseRequest aiRequest(RecommendationRequest request, EffectivePreference preference,
                                      List<Candidate> candidates, CourseContext context) {
        ProfileView profile = preferenceProfileService.getProfile(request.getUserId(), request.getCompanion().code());
        List<AiCourseRequest.TopCategory> top = (profile.lcls2Scores() == null ? Map.<String, Double>of()
                : profile.lcls2Scores()).entrySet().stream()
                .filter(e -> e.getValue() != null && e.getValue() > 0)
                .sorted(Map.Entry.<String, Double>comparingByValue(Comparator.reverseOrder()))
                .limit(TOP_PROFILE_CATEGORIES)
                .map(e -> new AiCourseRequest.TopCategory(e.getKey(), e.getValue()))
                .toList();
        DateTimeFormatter hhmm = DateTimeFormatter.ofPattern("HH:mm");
        AiCourseRequest.Conditions conditions = new AiCourseRequest.Conditions(request.getStartDate(),
                request.getEndDate(), request.getStartTime(), request.getCompanion().code(),
                request.getChildAgeGroup() == null ? null : request.getChildAgeGroup().name(),
                preference.pace().code(), request.isIndoorPreferred(), preference.timeOfDay().code(),
                new AiCourseRequest.Point(context.origin().lat(), context.origin().lng()));
        AiCourseRequest.Rules rules = new AiCourseRequest.Rules(COURSES,
                new AiCourseRequest.PlacesPerDay(context.minPerDay(), context.maxPerDay()),
                MealType.LUNCH.windowStart().format(hhmm) + "-" + MealType.LUNCH.windowEnd().format(hhmm),
                MealType.DINNER.windowStart().format(hhmm) + "-" + MealType.DINNER.windowEnd().format(hhmm),
                context.kids() ? "20:00" : null);
        return new AiCourseRequest(request.getId(), conditions, new AiCourseRequest.Profile(top),
                candidates.stream().map(RecommendationWorker::toAiCandidate).toList(), rules, List.of());
    }

    private static AiCourseRequest.Candidate toAiCandidate(Candidate candidate) {
        ContentSnapshot c = candidate.content();
        boolean festival = candidate.isFestival();
        return new AiCourseRequest.Candidate(c.id(), c.name(), c.lat(), c.lng(), c.categoryName(),
                c.ioType() == null ? null : c.ioType().name(),
                festival ? c.eventStartTime() : c.openTime(), festival ? c.eventEndTime() : c.closeTime(),
                c.lastEntryTime(),
                c.restDays() == null ? null : c.restDays().stream().sorted()
                        .map(d -> d.name().substring(0, 3)).toList(),
                festival ? c.eventStartDate() : null, festival ? c.eventEndDate() : null, candidate.required());
    }
}
