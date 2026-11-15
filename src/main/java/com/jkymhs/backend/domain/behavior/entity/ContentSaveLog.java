package com.jkymhs.backend.domain.behavior.entity;

import com.jkymhs.backend.domain.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

import java.time.LocalDateTime;

/**
 * 콘텐츠를 일정에 담아 저장한 기록 (인기 점수 입력). 행동 데이터 동의와 상관없이 남긴다.
 * 누가 무엇을 좋아하는지(취향)가 아니라 "얼마나 많이 담겼는지"(전체 통계)에 쓰기 때문이다.
 * 취향 프로필에 쓰는 행동 기록은 {@link BehaviorEvent}이고, 동의한 회원만 남는다.
 */
@Getter
@Entity
@Table(name = "content_save_logs",
        indexes = @Index(name = "idx_content_save_logs_created_at", columnList = "created_at"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContentSaveLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    // FK(users.id)를 만들기 위한 읽기 전용 연관. 값은 위 userId로 쓴다
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_content_save_logs_user_id"))
    private User user;

    @Column(name = "content_id", nullable = false)
    private Long contentId;

    // created_at만 있어서 BaseTimeEntity를 상속하지 않는다. 시각은 테스트에서 고정할 수 있게 Clock 값을 받는다
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    private ContentSaveLog(Long userId, Long contentId, LocalDateTime createdAt) {
        this.userId = userId;
        this.contentId = contentId;
        this.createdAt = createdAt;
    }

    public static ContentSaveLog create(Long userId, Long contentId, LocalDateTime now) {
        return new ContentSaveLog(userId, contentId, now);
    }
}
