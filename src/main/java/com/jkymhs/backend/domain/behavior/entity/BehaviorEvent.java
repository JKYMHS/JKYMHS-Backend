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

/**
 * 취향 프로필을 계산하는 행동 하나 (조회·찜·일정 저장·방문 만족도 등). 행동 데이터 수집에 동의한 회원만 남는다.
 * 동의를 끄거나 탈퇴하면 같은 트랜잭션에서 모두 지운다.
 *
 * lcls2(분류 중분류)는 저장 시점에 콘텐츠에서 복사해 둔다. 프로필을 다시 계산할 때마다 콘텐츠를 다시 읽지 않으려는 것이다.
 * 가중치(weight)도 복사한다. 나중에 가중치 표를 바꿔도 이미 쌓인 기록의 의미가 바뀌지 않게 하려는 것이다.
 * trip_id는 여행 도메인과 묶이지 않게 FK 없이 숫자만 둔다.
 * 프로필 계산이 "이 회원의 최근 180일"을 읽으므로 (user_id, occurred_at) 인덱스를 둔다.
 */
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

    // FK(users.id)를 만들기 위한 읽기 전용 연관. 값은 위 userId로 쓴다
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_behavior_events_user_id"))
    private User user;

    @Column(name = "content_id", nullable = false)
    private Long contentId;

    // 분류를 모르는 콘텐츠면 null (프로필 계산에서 빠진다)
    @Column(length = 6)
    private String lcls2;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "event_type", nullable = false, length = 20)
    private BehaviorSignalType eventType;

    @Column(nullable = false)
    private int weight;

    // 동행 FE 코드("kids" 등). 조회·찜처럼 동행을 모르는 행동은 null
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
