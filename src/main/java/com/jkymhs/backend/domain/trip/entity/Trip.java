package com.jkymhs.backend.domain.trip.entity;

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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * 여행 하나. 여행 아래에 일정안(plans)이 여러 개 있고, 그중 하나가 확정 일정안(confirmed_plan_id)이다.
 *
 * 인덱스: 여행 목록은 "회원의 다가오는 여행(종료일 ≥ 오늘) 시작일 순 → 지난 여행 시작일 역순"으로 커서를 넘긴다.
 * (user_id, start_date, id)로 정렬을 인덱스에서 끝내고, (user_id, end_date, id)는 "다가오는/지난" 구분과 방문 확인 대상 찾기에 쓴다.
 *
 * confirmed_plan_id에 FK를 걸지 않는 이유: plans.trip_id → trips, trips.confirmed_plan_id → plans로 서로를 가리키면
 * 여행을 만들 때(일정안이 아직 없음)와 지울 때 어느 쪽도 먼저 넣거나 지울 수 없다. 일정안 삭제 시 앱에서 null로 바꾼다.
 */
@Getter
@Entity
@Table(name = "trips", indexes = {
        @Index(name = "idx_trips_user_id_start_date", columnList = "user_id, start_date, id"),
        @Index(name = "idx_trips_user_id_end_date", columnList = "user_id, end_date, id")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Trip extends BaseTimeEntity {

    public static final int TITLE_MAX_LENGTH = 30;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 회원 id는 값으로 읽고, 아래 연관관계는 FK 제약을 만들기 위해서만 둔다 (Content의 지역 FK와 같은 방식)
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Getter(AccessLevel.NONE)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_trips_user_id"))
    private User user;

    @Column(nullable = false, length = TITLE_MAX_LENGTH)
    private String title;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private Companion companion;

    // 아이와 갈 때만 값이 있다
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 20)
    private ChildAgeGroup childAgeGroup;

    // 출발지 (집·역 등). 첫째 날 첫 장소까지의 이동을 계산할 때 쓴다. 없으면 모두 null
    @Column(length = 100)
    private String originName;

    private String originAddress;

    @Column(precision = 10, scale = 7)
    private BigDecimal originLat;

    @Column(precision = 10, scale = 7)
    private BigDecimal originLng;

    private Long confirmedPlanId;

    // 마지막으로 업로드한 구글 캘린더 id (캘린더 도메인이 TripCalendarSupport로 쓴다)
    private String calendarId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 20)
    private VisitCheckStatus visitCheckStatus;

    @Column(nullable = false)
    private int visitCheckDismissCount;

    // "나중에"를 누르면 이 날짜부터 다시 묻는다
    private LocalDate visitCheckNextAt;

    private LocalDateTime visitEmailSentAt;

    // 수정 충돌 확인용 버전 (G10). @Version은 쓰지 않는다 — 충돌 예외가 409로 바뀌지 않기 때문이다
    @Column(nullable = false)
    private int version;

    private Trip(Long userId, String title, LocalDate startDate, LocalDate endDate, Companion companion,
                 ChildAgeGroup childAgeGroup) {
        this.userId = userId;
        this.title = title;
        this.startDate = startDate;
        this.endDate = endDate;
        this.companion = companion;
        this.childAgeGroup = companion == Companion.KIDS ? childAgeGroup : null;
        this.visitCheckDismissCount = 0;
        this.version = 0;
    }

    public static Trip create(Long userId, String title, LocalDate startDate, LocalDate endDate, Companion companion,
                              ChildAgeGroup childAgeGroup, TripOrigin origin) {
        Trip trip = new Trip(userId, title, startDate, endDate, companion, childAgeGroup);
        trip.changeOrigin(origin);
        return trip;
    }

    /** 복제본. 캘린더·방문 확인 상태는 새 여행처럼 비운다 (§5 Q4) */
    public Trip copy(String newTitle) {
        Trip copy = new Trip(userId, newTitle, startDate, endDate, companion, childAgeGroup);
        copy.originName = originName;
        copy.originAddress = originAddress;
        copy.originLat = originLat;
        copy.originLng = originLng;
        return copy;
    }

    /** 여행 일수 (1박 2일 = 2) */
    public int days() {
        return (int) ChronoUnit.DAYS.between(startDate, endDate) + 1;
    }

    /** dayNumber(1부터)의 날짜 */
    public LocalDate dateOf(int dayNumber) {
        return startDate.plusDays(dayNumber - 1L);
    }

    public boolean isOwnedBy(Long userId) {
        return this.userId.equals(userId);
    }

    public boolean isEnded(LocalDate today) {
        return endDate.isBefore(today);
    }

    public TripOrigin origin() {
        if (originLat == null || originLng == null) {
            return null;
        }
        return new TripOrigin(originName, originAddress, originLat.doubleValue(), originLng.doubleValue());
    }

    public void rename(String title) {
        this.title = title;
    }

    public void changePeriod(LocalDate startDate, LocalDate endDate) {
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public void changeCompanion(Companion companion, ChildAgeGroup childAgeGroup) {
        this.companion = companion;
        this.childAgeGroup = companion == Companion.KIDS ? childAgeGroup : null;
    }

    public void changeOrigin(TripOrigin origin) {
        if (origin == null) {
            this.originName = null;
            this.originAddress = null;
            this.originLat = null;
            this.originLng = null;
            return;
        }
        this.originName = origin.name();
        this.originAddress = origin.address();
        this.originLat = BigDecimal.valueOf(origin.lat());
        this.originLng = BigDecimal.valueOf(origin.lng());
    }

    public void confirm(Long planId) {
        this.confirmedPlanId = planId;
    }

    public void clearConfirmedPlan() {
        this.confirmedPlanId = null;
    }

    public boolean isConfirmed(Long planId) {
        return confirmedPlanId != null && confirmedPlanId.equals(planId);
    }

    /** 캘린더 도메인이 업로드할 때 고른 캘린더를 기억한다 */
    public void useCalendar(String calendarId) {
        this.calendarId = calendarId;
    }

    public void increaseVersion() {
        this.version++;
    }

    // ---------- 방문 확인 ----------

    public boolean isVisitCheckClosed() {
        return visitCheckStatus == VisitCheckStatus.DONE || visitCheckStatus == VisitCheckStatus.DISMISSED;
    }

    /** 처음 방문 확인 대상이 되면 PENDING으로 바꾼다 (이미 상태가 있으면 그대로) */
    public void openVisitCheck() {
        if (visitCheckStatus == null) {
            visitCheckStatus = VisitCheckStatus.PENDING;
        }
    }

    public void completeVisitCheck() {
        this.visitCheckStatus = VisitCheckStatus.DONE;
        this.visitCheckNextAt = null;
    }

    public void reopenVisitCheck() {
        this.visitCheckStatus = VisitCheckStatus.PENDING;
    }

    /**
     * "나중에": 첫 번째는 {@code nextShowAt}부터 다시 묻고, 두 번째는 더 묻지 않는다 (DISMISSED).
     */
    public void dismissVisitCheck(LocalDate nextShowAt) {
        this.visitCheckDismissCount++;
        if (visitCheckDismissCount >= 2) {
            this.visitCheckStatus = VisitCheckStatus.DISMISSED;
            this.visitCheckNextAt = null;
        } else {
            this.visitCheckStatus = VisitCheckStatus.PENDING;
            this.visitCheckNextAt = nextShowAt;
        }
    }

    public void markVisitEmailSent(LocalDateTime now) {
        this.visitEmailSentAt = now;
    }
}
