package com.jkymhs.backend.domain.behavior.entity;

import com.jkymhs.backend.domain.user.entity.User;
import com.jkymhs.backend.global.event.BehaviorSignalType;
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

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "behavior_events",
        indexes = @Index(name = "idx_behavior_events_user_id_occurred_at", columnList = "user_id, occurred_at"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BehaviorEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_behavior_events_user_id"))
    private User user;

    @Column(name = "content_id", nullable = false)
    private Long contentId;

    @Column(length = 6)
    private String lcls2;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "event_type", nullable = false, length = 20)
    private BehaviorSignalType eventType;

    @Column(nullable = false)
    private int weight;

    @Column(length = 20)
    private String companion;

    @Column(name = "trip_id")
    private Long tripId;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    private BehaviorEvent(Long userId, Long contentId, String lcls2, BehaviorSignalType eventType, String companion,
                          Long tripId, LocalDateTime occurredAt) {
        this.userId = userId;
        this.contentId = contentId;
        this.lcls2 = lcls2;
        this.eventType = eventType;
        this.weight = eventType.getWeight();
        this.companion = companion;
        this.tripId = tripId;
        this.occurredAt = occurredAt;
    }

    public static BehaviorEvent create(Long userId, Long contentId, String lcls2, BehaviorSignalType eventType,
                                       String companion, Long tripId, LocalDateTime occurredAt) {
        return new BehaviorEvent(userId, contentId, lcls2, eventType, companion, tripId, occurredAt);
    }
}
