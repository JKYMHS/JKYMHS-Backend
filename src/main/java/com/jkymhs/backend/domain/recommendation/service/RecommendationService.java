package com.jkymhs.backend.domain.recommendation.service;

import com.jkymhs.backend.domain.content.entity.ContentType;
import com.jkymhs.backend.domain.content.service.ContentService;
import com.jkymhs.backend.domain.content.service.ContentSnapshot;
import com.jkymhs.backend.domain.recommendation.dto.request.RecommendationCreateRequest;
import com.jkymhs.backend.domain.recommendation.dto.request.RecommendationRetryRequest;
import com.jkymhs.backend.domain.recommendation.dto.response.RecommendationAcceptedResponse;
import com.jkymhs.backend.domain.recommendation.dto.response.RecommendationQuotaResponse;
import com.jkymhs.backend.domain.recommendation.dto.response.RecommendationStatusResponse;
import com.jkymhs.backend.domain.recommendation.entity.FailureReason;
import com.jkymhs.backend.domain.recommendation.entity.RecommendationConditions;
import com.jkymhs.backend.domain.recommendation.entity.RecommendationRequest;
import com.jkymhs.backend.domain.recommendation.entity.RecommendationRequestRegion;
import com.jkymhs.backend.domain.recommendation.entity.RecommendationRequiredPlace;
import com.jkymhs.backend.domain.recommendation.entity.RecommendationStatus;
import com.jkymhs.backend.domain.recommendation.entity.RecommendationStep;
import com.jkymhs.backend.domain.recommendation.repository.RecommendationRequestRegionRepository;
import com.jkymhs.backend.domain.recommendation.repository.RecommendationRequestRepository;
import com.jkymhs.backend.domain.recommendation.repository.RecommendationRequiredPlaceRepository;
import com.jkymhs.backend.domain.region.service.RegionRef;
import com.jkymhs.backend.domain.region.service.RegionService;
import com.jkymhs.backend.domain.trip.entity.PlanSource;
import com.jkymhs.backend.domain.trip.service.TripService;
import com.jkymhs.backend.domain.user.service.PreferenceService;
import com.jkymhs.backend.domain.user.service.UserService;
import com.jkymhs.backend.global.exception.CustomException;
import com.jkymhs.backend.global.exception.ErrorCode;
import com.jkymhs.backend.global.model.ChildAgeGroup;
import com.jkymhs.backend.global.model.Companion;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * AI 추천 요청·다시 추천·결과 조회·남은 횟수.
 *
 * <b>요청 흐름</b>: 입력 검증 → (트랜잭션) 회원 행 잠금 → 같은 조건 PENDING이 10초 안에 있으면 그 요청을 돌려줌 → 오늘 횟수 확인(429)
 * → PENDING 행 저장 → 커밋 → 실행기에 작업을 넣고 202. 회원 행을 잠그는 이유: 같은 회원이 동시에 두 번 눌러도 "센 다음 넣기"가
 * 겹치지 않아 10회를 넘기지 않는다. 작업은 커밋 뒤에 넣는다 (커밋 전에 넣으면 작업 스레드가 아직 없는 행을 읽을 수 있다).
 *
 * <b>횟수</b>: 오늘(KST) 만든 요청 중 FAILED가 아닌 것. 기본 코스(FALLBACK)도 결과를 받았으니 센다.
 */
@Slf4j
@Service
public class RecommendationService {

    static final long POLL_AFTER_MS = 2000;
    static final int MAX_DAYS = 4;
    static final int MAX_REQUIRED = 5;
    static final String NOT_FOUND_MESSAGE = "추천 결과가 만료되었거나 존재하지 않습니다.";
    static final String RETRY_NOT_FOUND_MESSAGE = "이전 추천 결과가 만료되었습니다. 조건을 다시 입력해주세요.";
    static final String CONTENT_NOT_FOUND_MESSAGE = "존재하지 않거나 삭제된 장소입니다.";
    static final String OUT_OF_PERIOD = "OUT_OF_PERIOD";
    static final String OUT_OF_REGION = "OUT_OF_REGION";
    private static final DateTimeFormatter RESET_AT_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

    private final RecommendationRequestRepository requestRepository;
    private final RecommendationRequestRegionRepository regionRepository;
    private final RecommendationRequiredPlaceRepository requiredPlaceRepository;
    private final RecommendationResultStore resultStore;
    private final RecommendationWorker worker;
    private final RecommendationProperties properties;
    private final UserService userService;
    private final PreferenceService preferenceService;
    private final RegionService regionService;
    private final ContentService contentService;
    private final TripService tripService;
    private final TaskExecutor executor;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public RecommendationService(RecommendationRequestRepository requestRepository,
                                 RecommendationRequestRegionRepository regionRepository,
                                 RecommendationRequiredPlaceRepository requiredPlaceRepository,
                                 RecommendationResultStore resultStore, RecommendationWorker worker,
                                 RecommendationProperties properties, UserService userService,
                                 PreferenceService preferenceService, RegionService regionService,
                                 ContentService contentService, TripService tripService,
                                 @Qualifier(RecommendationConfig.EXECUTOR) TaskExecutor executor,
                                 TransactionTemplate transactionTemplate, Clock clock) {
        this.requestRepository = requestRepository;
        this.regionRepository = regionRepository;
        this.requiredPlaceRepository = requiredPlaceRepository;
        this.resultStore = resultStore;
        this.worker = worker;
        this.properties = properties;
        this.userService = userService;
        this.preferenceService = preferenceService;
        this.regionService = regionService;
        this.contentService = contentService;
        this.tripService = tripService;
        this.executor = executor;
        this.transactionTemplate = transactionTemplate;
        this.clock = clock;
    }

    // ---------- 남은 횟수 ----------

    public RecommendationQuotaResponse quota(Long userId) {
        LocalDate today = LocalDate.now(clock);
        int used = (int) usedToday(userId, today);
        int total = properties.dailyLimit();
        String resetAt = today.plusDays(1).atStartOfDay(clock.getZone()).format(RESET_AT_FORMAT);
        Companion lastCompanion = userService.getLastCompanion(userId).orElse(null);
        LocalTime startTime = preferenceService.getEffective(userId).timeOfDay().startTime();
        return new RecommendationQuotaResponse(total, used, Math.max(0, total - used), resetAt,
                new RecommendationQuotaResponse.Defaults(lastCompanion, startTime));
    }

    // ---------- 요청 ----------

    /**
     * @throws CustomException 400 SCHEDULE_LIMIT_EXCEEDED(기간·지난 날짜·꼭 포함 6개 이상), 400 INVALID_INPUT(아이 나이, 지역),
     *                         403/404 targetTripId, 404 꼭 포함 장소, 429 AI_DAILY_LIMIT_EXCEEDED, 503 AI_SERVICE_UNAVAILABLE
     */
    public RecommendationAcceptedResponse create(Long userId, RecommendationCreateRequest body) {
        LocalDateTime now = LocalDateTime.now(clock);
        validatePeriod(body.startDate(), body.endDate(), now.toLocalDate());
        List<Long> districtIds = body.districtIdList();
        regionService.validateDistricts(body.regionId(), districtIds);
        if (body.companion() == Companion.KIDS && body.childAgeGroup() == null) {
            throw new CustomException(ErrorCode.INVALID_INPUT, "아이 나이를 선택해주세요.", "아이 동행인데 나이 없음");
        }
        List<Long> requiredIds = body.requiredIdList();
        if (requiredIds.size() > MAX_REQUIRED) {
            throw new CustomException(ErrorCode.SCHEDULE_LIMIT_EXCEEDED, "꼭 포함할 장소는 최대 5개까지 고를 수 있습니다.",
                    "꼭 포함 장소 " + requiredIds.size() + "개");
        }
        List<RecommendationAcceptedResponse.Warning> warnings = requiredWarnings(requiredIds, body, districtIds);
        if (body.targetTripId() != null) {
            tripService.getOwnedTrip(userId, body.targetTripId());
        }
        LocalTime startTime = body.startTime() != null ? body.startTime()
                : preferenceService.getEffective(userId).timeOfDay().startTime();
        ChildAgeGroup childAgeGroup = body.companion() == Companion.KIDS ? body.childAgeGroup() : null;
        RecommendationConditions conditions = new RecommendationConditions(body.regionId(), body.startDate(),
                body.endDate(), startTime, body.origin().name().trim(), body.origin().address(), body.origin().lat(),
                body.origin().lng(), body.companion(), childAgeGroup, body.indoorPreferred(), body.targetTripId());

        Submitted submitted = submit(userId, conditions, districtIds, requiredIds, PlanSource.AI, null, List.of(), now);
        return new RecommendationAcceptedResponse(submitted.requestId(), null, RecommendationStatus.PENDING,
                submitted.left(), POLL_AFTER_MS, warnings);
    }

    /**
     * 직전 결과와 같은 조건으로 한 번 더 추천한다 (1회 차감). 빼달라고 한 곳과 직전 코스의 장소는 점수를 깎는다.
     *
     * @throws CustomException 404 이전 결과 만료, 429, 503
     */
    public RecommendationAcceptedResponse retry(Long userId, Long requestId, RecommendationRetryRequest body) {
        LocalDateTime now = LocalDateTime.now(clock);
        RecommendationRequest previous = requestRepository.findByIdAndUserId(requestId, userId)
                .filter(r -> !r.isExpired(now) && r.getStatus().hasResult() && r.hasOrigin())
                .orElseThrow(() -> retryNotFound("이전 요청 없음·만료·결과 없음: " + requestId));
        if (resultStore.findResult(previous.getId()).isEmpty()) {
            throw retryNotFound("이전 결과 Redis 없음: " + requestId);
        }
        List<Long> districtIds = districtIdsOf(previous.getId());
        List<Long> requiredIds = requiredIdsOf(previous.getId());
        RecommendationConditions conditions = new RecommendationConditions(previous.getSidoRegionId(),
                previous.getStartDate(), previous.getEndDate(), previous.getStartTime(), previous.getOriginName(),
                previous.getOriginAddress(), previous.getOriginLat().doubleValue(), previous.getOriginLng().doubleValue(),
                previous.getCompanion(), previous.getChildAgeGroup(), previous.isIndoorPreferred(),
                previous.getTargetTripId());
        RecommendationRetryRequest retry = body == null ? RecommendationRetryRequest.EMPTY : body;

        Submitted submitted = submit(userId, conditions, districtIds, requiredIds, PlanSource.AI_RETRY,
                previous.getId(), retry.excludeIds(), now);
        return new RecommendationAcceptedResponse(submitted.requestId(), previous.getId(), RecommendationStatus.PENDING,
                submitted.left(), POLL_AFTER_MS, List.of());
    }

    // ---------- 결과 ----------

    /**
     * @throws CustomException 404 "추천 결과가 만료되었거나 존재하지 않습니다." (다른 회원, 만료, Redis 결과 없음)
     */
    public RecommendationStatusResponse getStatus(Long userId, Long requestId) {
        LocalDateTime now = LocalDateTime.now(clock);
        RecommendationRequest request = requestRepository.findByIdAndUserId(requestId, userId)
                .filter(r -> !r.isExpired(now))
                .orElseThrow(() -> notFound("요청 없음·다른 회원·만료: " + requestId));
        return switch (request.getStatus()) {
            case PENDING -> {
                RecommendationResultStore.Progress progress = resultStore.findProgress(requestId)
                        .orElse(new RecommendationResultStore.Progress(RecommendationStep.QUEUED, 0, 0));
                yield new RecommendationStatusResponse.Pending(requestId, RecommendationStatus.PENDING, progress.step(),
                        progress.step().index(), RecommendationStep.TOTAL, POLL_AFTER_MS,
                        new RecommendationStatusResponse.Progress(progress.candidateCount(), progress.selectedCount()));
            }
            case FAILED -> new RecommendationStatusResponse.Failed(requestId, RecommendationStatus.FAILED, true,
                    request.getFailureReason() == null ? FailureReason.INTERNAL : request.getFailureReason());
            case DONE, FALLBACK -> {
                RecommendationResult result = resultStore.findResult(requestId)
                        .orElseThrow(() -> notFound("Redis 결과 없음: " + requestId));
                yield new RecommendationStatusResponse.Result(requestId, request.getStatus(),
                        request.getStatus() == RecommendationStatus.FALLBACK, result.indoorShortage(),
                        request.getPreviousRequestId(), request.getExpiresAt(), conditions(request), result.courses());
            }
        };
    }

    // ---------- 내부 ----------

    private Submitted submit(Long userId, RecommendationConditions conditions, List<Long> districtIds,
                             List<Long> requiredIds, PlanSource source, Long previousRequestId, List<Long> excludedIds,
                             LocalDateTime now) {
        String key = conditionsKey(conditions, districtIds, requiredIds, previousRequestId, excludedIds);
        Submitted submitted = transactionTemplate.execute(status -> {
            userService.getByIdForUpdate(userId);
            long used = usedToday(userId, now.toLocalDate());
            Optional<RecommendationRequest> duplicate = requestRepository
                    .findFirstByUserIdAndStatusAndConditionsKeyAndRequestedAtAfterOrderByIdDesc(userId,
                            RecommendationStatus.PENDING, key, now.minus(properties.dedupeWindow()));
            if (duplicate.isPresent()) {
                return new Submitted(duplicate.get().getId(), left(used), false);
            }
            if (used >= properties.dailyLimit()) {
                throw new CustomException(ErrorCode.AI_DAILY_LIMIT_EXCEEDED, "오늘 " + used + "회 사용");
            }
            RecommendationRequest request = requestRepository.save(RecommendationRequest.create(userId, conditions,
                    source, previousRequestId, excludedIds, key, now, properties.resultTtl()));
            regionRepository.saveAll(districtIds.stream()
                    .map(id -> RecommendationRequestRegion.of(request.getId(), id)).toList());
            List<RecommendationRequiredPlace> required = new ArrayList<>();
            for (int i = 0; i < requiredIds.size(); i++) {
                required.add(RecommendationRequiredPlace.of(request.getId(), requiredIds.get(i), i + 1));
            }
            requiredPlaceRepository.saveAll(required);
            userService.rememberCompanion(userId, conditions.companion());
            return new Submitted(request.getId(), left(used + 1), true);
        });
        if (submitted.created()) {
            dispatch(submitted.requestId());
        }
        return submitted;
    }

    private void dispatch(Long requestId) {
        try {
            executor.execute(() -> worker.run(requestId));
        } catch (TaskRejectedException e) {
            log.warn("추천 실행기가 가득 참: requestId={}", requestId);
            requestRepository.finish(requestId, RecommendationStatus.FAILED, FailureReason.AI_UNAVAILABLE);
            throw new CustomException(ErrorCode.AI_SERVICE_UNAVAILABLE, "추천 실행기 포화");
        }
    }

    private long usedToday(Long userId, LocalDate today) {
        return requestRepository.countByUserIdAndRequestDateAndStatusNot(userId, today, RecommendationStatus.FAILED);
    }

    private int left(long used) {
        return (int) Math.max(0, properties.dailyLimit() - used);
    }

    private static void validatePeriod(LocalDate start, LocalDate end, LocalDate today) {
        if (start.isBefore(today)) {
            throw new CustomException(ErrorCode.SCHEDULE_LIMIT_EXCEEDED, "지난 날짜는 선택할 수 없습니다.",
                    "가는 날 " + start + " < 오늘 " + today);
        }
        if (end.isBefore(start)) {
            throw new CustomException(ErrorCode.INVALID_INPUT, "오는 날은 가는 날보다 빠를 수 없습니다.",
                    "오는 날 " + end + " < 가는 날 " + start);
        }
        if (end.isAfter(start.plusDays(MAX_DAYS - 1))) {
            throw new CustomException(ErrorCode.SCHEDULE_LIMIT_EXCEEDED, "여행 기간은 최대 3박 4일까지 선택할 수 있습니다.",
                    "기간 " + start + "~" + end);
        }
    }

    // 꼭 포함할 장소가 없거나 지워졌으면 404, 기간·지역이 안 맞으면 경고만 (FE: 고를 수는 있게 둔다, §4 #22)
    private List<RecommendationAcceptedResponse.Warning> requiredWarnings(List<Long> requiredIds,
                                                                          RecommendationCreateRequest body,
                                                                          List<Long> districtIds) {
        Map<Long, ContentSnapshot> contents = contentService.findSnapshots(requiredIds);
        List<RecommendationAcceptedResponse.Warning> warnings = new ArrayList<>();
        for (Long id : requiredIds) {
            ContentSnapshot content = contents.get(id);
            if (content == null || content.deleted()) {
                throw new CustomException(ErrorCode.RESOURCE_NOT_FOUND, CONTENT_NOT_FOUND_MESSAGE,
                        "꼭 포함 장소 없음·삭제: " + id);
            }
            boolean inRegion = body.regionId().equals(content.sidoRegionId())
                    && (districtIds.isEmpty() || districtIds.contains(content.regionId()));
            if (!inRegion) {
                warnings.add(new RecommendationAcceptedResponse.Warning(id, OUT_OF_REGION, null, null));
            }
            if (content.contentType() == ContentType.FESTIVAL && content.eventStartDate() != null
                    && content.eventEndDate() != null
                    && (content.eventEndDate().isBefore(body.startDate()) || content.eventStartDate().isAfter(body.endDate()))) {
                warnings.add(new RecommendationAcceptedResponse.Warning(id, OUT_OF_PERIOD, content.eventStartDate(),
                        content.eventEndDate()));
            }
        }
        return warnings;
    }

    private RecommendationStatusResponse.Conditions conditions(RecommendationRequest request) {
        List<Long> districtIds = districtIdsOf(request.getId());
        List<Long> regionIds = new ArrayList<>(districtIds);
        regionIds.add(request.getSidoRegionId());
        Map<Long, RegionRef> regions = regionService.getRegions(regionIds);
        RegionRef sido = regions.get(request.getSidoRegionId());
        return new RecommendationStatusResponse.Conditions(request.getSidoRegionId(), sido == null ? null : sido.name(),
                districtIds,
                districtIds.stream().map(regions::get).map(r -> r == null ? null : r.name()).toList(),
                request.getStartDate(), request.getEndDate(), request.getStartTime(), request.getCompanion(),
                request.getChildAgeGroup(), request.isIndoorPreferred(),
                new RecommendationStatusResponse.Origin(request.getOriginName(), request.getOriginAddress(),
                        request.getOriginLat() == null ? null : request.getOriginLat().doubleValue(),
                        request.getOriginLng() == null ? null : request.getOriginLng().doubleValue()));
    }

    List<Long> districtIdsOf(Long requestId) {
        return regionRepository.findAllByRequestIdOrderByIdAsc(requestId).stream()
                .map(RecommendationRequestRegion::getRegionId).toList();
    }

    private List<Long> requiredIdsOf(Long requestId) {
        return requiredPlaceRepository.findAllByRequestIdOrderBySortOrderAsc(requestId).stream()
                .map(RecommendationRequiredPlace::getContentId).toList();
    }

    static String conditionsKey(RecommendationConditions c, List<Long> districtIds, List<Long> requiredIds,
                                Long previousRequestId, List<Long> excludedIds) {
        String raw = String.join("|", String.valueOf(c.sidoRegionId()),
                districtIds.stream().sorted().toList().toString(), String.valueOf(c.startDate()),
                String.valueOf(c.endDate()), String.valueOf(c.startTime()), c.originName(),
                String.valueOf(c.originAddress()), String.valueOf(c.originLat()), String.valueOf(c.originLng()),
                c.companion().name(), String.valueOf(c.childAgeGroup()), String.valueOf(c.indoor()),
                requiredIds.toString(), String.valueOf(c.targetTripId()), String.valueOf(previousRequestId),
                excludedIds.stream().sorted().toList().toString());
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 없음", e);
        }
    }

    private static CustomException notFound(String description) {
        return new CustomException(ErrorCode.RESOURCE_NOT_FOUND, NOT_FOUND_MESSAGE, description);
    }

    private static CustomException retryNotFound(String description) {
        return new CustomException(ErrorCode.RESOURCE_NOT_FOUND, RETRY_NOT_FOUND_MESSAGE, description);
    }

    private record Submitted(Long requestId, int left, boolean created) {
    }
}
