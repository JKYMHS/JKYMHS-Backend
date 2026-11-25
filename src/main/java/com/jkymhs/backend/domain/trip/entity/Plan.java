package com.jkymhs.backend.domain.trip.entity;

import com.jkymhs.backend.global.entity.BaseTimeEntity;
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

@Getter
@Entity
@Table(name = "plans", indexes = @Index(name = "idx_plans_trip_id", columnList = "trip_id, id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Plan extends BaseTimeEntity {
    public static final int NAME_MAX_LENGTH = 30;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "trip_id", nullable = false)
    private Long tripId;

    @Getter(AccessLevel.NONE)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_plans_trip_id"))
    private Trip trip;

    @Column(nullable = false, length = NAME_MAX_LENGTH)
    private String name;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 10)
    private PlanSource source;

    private Long aiRequestId;

    @Column(nullable = false)
    private int totalMoveMinutes;

    @Column(nullable = false)
    private int version;

    private Plan(Long tripId, String name, PlanSource source, Long aiRequestId) {
        this.tripId = tripId;
        this.name = name;
        this.source = source;
        this.aiRequestId = aiRequestId;
        this.totalMoveMinutes = 0;
        this.version = 0;
    }

    public static Plan create(Long tripId, String name, PlanSource source, Long aiRequestId) {
        return new Plan(tripId, name, source, aiRequestId);
    }

    public Plan copyTo(Long tripId, String newName) {
        Plan copy = new Plan(tripId, newName, source, aiRequestId);
        copy.totalMoveMinutes = totalMoveMinutes;
        return copy;
    }

    public void rename(String name) {
        this.name = name;
    }

    public void changeTotalMoveMinutes(int totalMoveMinutes) {
        this.totalMoveMinutes = totalMoveMinutes;
    }

    public void increaseVersion() {
        this.version++;
    }
}
