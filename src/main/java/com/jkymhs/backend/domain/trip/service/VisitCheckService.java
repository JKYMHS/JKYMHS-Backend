package com.jkymhs.backend.domain.trip.service;

import com.jkymhs.backend.domain.trip.dto.request.VisitCheckSaveRequest;
import com.jkymhs.backend.domain.trip.dto.response.VisitCheckDismissResponse;
import com.jkymhs.backend.domain.trip.dto.response.VisitCheckPendingResponse;
import com.jkymhs.backend.domain.trip.dto.response.VisitCheckResponse;
import com.jkymhs.backend.domain.trip.dto.response.VisitCheckSaveResponse;
import com.jkymhs.backend.domain.trip.entity.PlanItem;
import com.jkymhs.backend.domain.trip.entity.Trip;
import com.jkymhs.backend.domain.trip.entity.VisitCheckStatus;
import com.jkymhs.backend.domain.trip.entity.VisitReview;
import com.jkymhs.backend.domain.trip.repository.PlanItemRepository;
import com.jkymhs.backend.domain.trip.repository.TripRepository;
import com.jkymhs.backend.domain.trip.repository.VisitReviewRepository;
import com.jkymhs.backend.domain.user.entity.ConsentType;
import com.jkymhs.backend.domain.user.service.UserConsentService;
import com.jkymhs.backend.global.event.BehaviorSignalEvent;
import com.jkymhs.backend.global.event.BehaviorSignalType;
import com.jkymhs.backend.global.exception.CustomException;
import com.jkymhs.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VisitCheckService {
    private static final String RATING_RANGE = "만족도는 1~5 사이로 입력해주세요.";
    private static final String UNKNOWN_ITEM = "방문 확인할 수 없는 장소입니다.";

    private final TripAccess tripAccess;
    private final TripRepository tripRepository;
    private final PlanItemRepository planItemRepository;
    private final VisitReviewRepository visitReviewRepository;
    private final UserConsentService userConsentService;
    private final VisitCheckProperties properties;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    @Transactional
    public Optional<Trip> findPendingTrip(Long userId) {
        if (!userConsentService.isAgreed(userId, ConsentType.BEHAVIOR_DATA)) {
            return Optional.empty();
        }
        LocalDate today = LocalDate.now(clock);
        for (Trip trip : tripRepository.findVisitCheckCandidates(userId, today)) {
            if (planItemRepository.countByPlanId(trip.getConfirmedPlanId()) > 0) {
                trip.openVisitCheck();
                return Optional.of(trip);
            }
        }
        return Optional.empty();
    }

    @Transactional
    public VisitCheckPendingResponse getPending(Long userId) {
        return new VisitCheckPendingResponse(findPendingTrip(userId)
                .map(trip -> new VisitCheckPendingResponse.Trip(trip.getId(), trip.getTitle(), trip.getEndDate()))
                .orElse(null));
    }

    boolean isEligible(Trip trip, LocalDate today) {
        return trip.isEnded(today) && trip.getConfirmedPlanId() != null && !trip.isVisitCheckClosed()
                && (trip.getVisitCheckNextAt() == null || !trip.getVisitCheckNextAt().isAfter(today))
                && planItemRepository.countByPlanId(trip.getConfirmedPlanId()) > 0;
    }

    @Transactional
    public VisitCheckResponse get(Long userId, Long tripId) {
        Trip trip = tripAccess.ownedTrip(userId, tripId);
        List<PlanItem> items = availableItems(trip);
        trip.openVisitCheck();
        Map<Long, VisitReview> reviews = reviews(items);

        Map<Integer, List<PlanItem>> byDay = items.stream().collect(Collectors.groupingBy(PlanItem::getDayNumber));
        List<VisitCheckResponse.Day> days = new ArrayList<>();
        byDay.keySet().stream().sorted().forEach(day -> days.add(new VisitCheckResponse.Day(day, trip.dateOf(day),
                byDay.get(day).stream().map(item -> {
                    VisitReview review = reviews.get(item.getId());
                    return new VisitCheckResponse.Item(item.getId(), item.getContentId(), item.getSnapshotName(),
                            item.getSnapshotImageUrl(), review == null ? null : review.isVisited(),
                            review == null ? null : review.getRating());
                }).toList())));
        return new VisitCheckResponse(trip.getId(), trip.getTitle(), trip.getVisitCheckStatus(), reviews.size(),
                items.size(), days);
    }

    @Transactional
    public VisitCheckSaveResponse save(Long userId, Long tripId, VisitCheckSaveRequest request) {
        Trip trip = tripAccess.ownedTripForUpdate(userId, tripId);
        List<PlanItem> items = availableItems(trip);
        Map<Long, PlanItem> itemsById = items.stream().collect(Collectors.toMap(PlanItem::getId, Function.identity()));
        for (VisitCheckSaveRequest.Item answer : request.items()) {
            if (!itemsById.containsKey(answer.itemId())) {
                throw new CustomException(ErrorCode.INVALID_INPUT, UNKNOWN_ITEM, "확정 일정안에 없는 항목: " + answer.itemId());
            }
            Integer rating = answer.rating();
            boolean valid = answer.visited() ? rating != null && rating >= 1 && rating <= 5
                    : rating == null || rating == 0;
            if (!valid) {
                throw new CustomException(ErrorCode.INVALID_INPUT, RATING_RANGE, "visited=" + answer.visited()
                        + ", rating=" + rating);
            }
        }

        Map<Long, VisitReview> reviews = reviews(items);
        LocalDateTime now = LocalDateTime.now(clock);
        for (VisitCheckSaveRequest.Item answer : request.items()) {
            Integer rating = answer.visited() ? answer.rating() : null;
            VisitReview review = reviews.get(answer.itemId());
            if (review != null) {
                review.answer(answer.visited(), rating);
                continue;
            }
            reviews.put(answer.itemId(), visitReviewRepository.save(
                    VisitReview.create(userId, answer.itemId(), answer.visited(), rating)));
            signalType(answer.visited(), rating).ifPresent(type -> eventPublisher.publishEvent(new BehaviorSignalEvent(
                    userId, itemsById.get(answer.itemId()).getContentId(), type, trip.getCompanion().code(),
                    trip.getId(), now)));
        }

        if (reviews.size() >= items.size()) {
            trip.completeVisitCheck();
        } else {
            trip.reopenVisitCheck();
        }
        return new VisitCheckSaveResponse(trip.getVisitCheckStatus(), reviews.size(), items.size());
    }

    @Transactional
    public VisitCheckDismissResponse dismiss(Long userId, Long tripId) {
        Trip trip = tripAccess.ownedTripForUpdate(userId, tripId);
        availableItems(trip);
        if (trip.getVisitCheckStatus() == VisitCheckStatus.DONE) {
            throw notAvailable("이미 확인을 마친 여행");
        }
        trip.dismissVisitCheck(LocalDate.now(clock).plusDays(properties.dismissDays()));
        return new VisitCheckDismissResponse(trip.getVisitCheckStatus(), trip.getVisitCheckNextAt());
    }

    private List<PlanItem> availableItems(Trip trip) {
        if (!trip.isEnded(LocalDate.now(clock))) {
            throw notAvailable("아직 끝나지 않은 여행");
        }
        if (trip.getConfirmedPlanId() == null) {
            throw notAvailable("확정 일정안 없음");
        }
        if (trip.getVisitCheckStatus() == VisitCheckStatus.DISMISSED) {
            throw notAvailable("방문 확인을 닫은 여행");
        }
        List<PlanItem> items = planItemRepository.findAllByPlanIdOrderByDayNumberAscSortOrderAscIdAsc(
                trip.getConfirmedPlanId());
        if (items.isEmpty()) {
            throw notAvailable("확정 일정안에 장소 없음");
        }
        return items;
    }

    private Map<Long, VisitReview> reviews(List<PlanItem> items) {
        return visitReviewRepository.findAllByPlanItemIdIn(items.stream().map(PlanItem::getId).toList()).stream()
                .collect(Collectors.toMap(VisitReview::getPlanItemId, Function.identity()));
    }

    private static Optional<BehaviorSignalType> signalType(boolean visited, Integer rating) {
        if (!visited || rating == null) {
            return Optional.empty();
        }
        if (rating >= 4) {
            return Optional.of(BehaviorSignalType.VISIT_SATISFIED);
        }
        if (rating == 3) {
            return Optional.of(BehaviorSignalType.VISIT_NEUTRAL);
        }
        return Optional.of(BehaviorSignalType.VISIT_UNSATISFIED);
    }

    private static CustomException notAvailable(String description) {
        return new CustomException(ErrorCode.VISIT_CHECK_NOT_AVAILABLE, description);
    }
}
