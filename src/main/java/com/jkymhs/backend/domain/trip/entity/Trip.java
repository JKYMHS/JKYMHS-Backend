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

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 20)
    private ChildAgeGroup childAgeGroup;

    @Column(length = 100)
    private String originName;

    private String originAddress;

    @Column(precision = 10, scale = 7)
    private BigDecimal originLat;

    @Column(precision = 10, scale = 7)
    private BigDecimal originLng;

    private Long confirmedPlanId;

    private String calendarId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 20)
    private VisitCheckStatus visitCheckStatus;

    @Column(nullable = false)
    private int visitCheckDismissCount;

    private LocalDate visitCheckNextAt;

    private LocalDateTime visitEmailSentAt;

    // @Version은 충돌 예외가 409로 안 바뀌어 쓰지 않음
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

    public Trip copy(String newTitle) {
        Trip copy = new Trip(userId, newTitle, startDate, endDate, companion, childAgeGroup);
        copy.originName = originName;
        copy.originAddress = originAddress;
        copy.originLat = originLat;
        copy.originLng = originLng;
        return copy;
    }

    public int days() {
        return (int) ChronoUnit.DAYS.between(startDate, endDate) + 1;
    }

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

    public void useCalendar(String calendarId) {
        this.calendarId = calendarId;
    }

    public void increaseVersion() {
        this.version++;
    }

    public boolean isVisitCheckClosed() {
        return visitCheckStatus == VisitCheckStatus.DONE || visitCheckStatus == VisitCheckStatus.DISMISSED;
    }

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
