package com.jkymhs.backend.domain.recommendation.entity;

import com.jkymhs.backend.domain.region.entity.Region;
import com.jkymhs.backend.domain.trip.entity.PlanSource;
import com.jkymhs.backend.domain.user.entity.User;
import com.jkymhs.backend.global.entity.BaseTimeEntity;
import com.jkymhs.backend.global.model.ChildAgeGroup;
import com.jkymhs.backend.global.model.Companion;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@Entity
@Table(name = "recommendation_requests", indexes = {
        @Index(name = "idx_recommendation_requests_user_id_request_date", columnList = "user_id, request_date, status"),
        @Index(name = "idx_recommendation_requests_status_requested_at", columnList = "status, requested_at")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecommendationRequest extends BaseTimeEntity {
    public static final int EXCLUDED_IDS_MAX_LENGTH = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Getter(AccessLevel.NONE)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_recommendation_requests_user_id"))
    private User user;

    @Column(name = "sido_region_id", nullable = false)
    private Long sidoRegionId;

    @Getter(AccessLevel.NONE)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sido_region_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_recommendation_requests_sido_region_id"))
    private Region sidoRegion;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;

    @Column(nullable = false)
    private LocalTime startTime;

    @Column(nullable = false, length = 100)
    private String originName;

    // 현재 위치일 수 있어 24시간 뒤 배치가 지움
    private String originAddress;

    @Column(precision = 10, scale = 7)
    private BigDecimal originLat;

    @Column(precision = 10, scale = 7)
    private BigDecimal originLng;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private Companion companion;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 20)
    private ChildAgeGroup childAgeGroup;

    @Column(nullable = false)
    private boolean indoorPreferred;

    private Long targetTripId;

    private Long previousRequestId;

    @Column(length = EXCLUDED_IDS_MAX_LENGTH)
    private String excludedContentIds;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 10)
    private PlanSource source;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private RecommendationStatus status;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 50)
    private FailureReason failureReason;

    @Column(nullable = false)
    private LocalDate requestDate;

    @Column(nullable = false)
    private LocalDateTime requestedAt;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false, length = 64)
    private String conditionsKey;

    private RecommendationRequest(Long userId, RecommendationConditions conditions, PlanSource source,
                                  Long previousRequestId, Collection<Long> excludedContentIds, String conditionsKey,
                                  LocalDateTime now, Duration resultTtl) {
        this.userId = userId;
        this.sidoRegionId = conditions.sidoRegionId();
        this.startDate = conditions.startDate();
        this.endDate = conditions.endDate();
        this.startTime = conditions.startTime();
        this.originName = conditions.originName();
        this.originAddress = conditions.originAddress();
        this.originLat = BigDecimal.valueOf(conditions.originLat()).setScale(7, RoundingMode.HALF_UP);
        this.originLng = BigDecimal.valueOf(conditions.originLng()).setScale(7, RoundingMode.HALF_UP);
        this.companion = conditions.companion();
        this.childAgeGroup = conditions.childAgeGroup();
        this.indoorPreferred = conditions.indoor();
        this.targetTripId = conditions.targetTripId();
        this.previousRequestId = previousRequestId;
        this.excludedContentIds = joinIds(excludedContentIds);
        this.source = source;
        this.status = RecommendationStatus.PENDING;
        this.requestDate = now.toLocalDate();
        this.requestedAt = now;
        this.expiresAt = now.plus(resultTtl);
        this.conditionsKey = conditionsKey;
    }

    public static RecommendationRequest create(Long userId, RecommendationConditions conditions, PlanSource source,
                                               Long previousRequestId, Collection<Long> excludedContentIds,
                                               String conditionsKey, LocalDateTime now, Duration resultTtl) {
        return new RecommendationRequest(userId, conditions, source, previousRequestId, excludedContentIds,
                conditionsKey, now, resultTtl);
    }

    public boolean isExpired(LocalDateTime now) {
        return !now.isBefore(expiresAt);
    }

    public int days() {
        return (int) (endDate.toEpochDay() - startDate.toEpochDay()) + 1;
    }

    public boolean hasOrigin() {
        return originLat != null && originLng != null;
    }

    public List<Long> excludedIds() {
        if (excludedContentIds == null || excludedContentIds.isBlank()) {
            return List.of();
        }
        return Arrays.stream(excludedContentIds.split(",")).map(String::trim).filter(s -> !s.isEmpty())
                .map(Long::valueOf).toList();
    }

    private static String joinIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return null;
        }
        StringBuilder joined = new StringBuilder();
        for (Long id : ids.stream().distinct().collect(Collectors.toList())) {
            String next = (joined.isEmpty() ? "" : ",") + id;
            if (joined.length() + next.length() > EXCLUDED_IDS_MAX_LENGTH) {
                break;
            }
            joined.append(next);
        }
        return joined.toString();
    }
}
