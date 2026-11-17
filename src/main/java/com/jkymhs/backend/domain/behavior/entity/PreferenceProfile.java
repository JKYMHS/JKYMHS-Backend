package com.jkymhs.backend.domain.behavior.entity;

import com.jkymhs.backend.domain.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 회원의 분류(lcls2)별 선호 점수. AI 추천이 후보를 고를 때 쓴다.
 * profile_key는 {@code common}(모든 행동) 또는 동행 코드("kids" 등, 그 동행으로 여행할 때의 행동만)다.
 *
 * 행동이 들어올 때마다 {@link com.jkymhs.backend.domain.behavior.service.PreferenceProfileService}가 다시 계산해서
 * {@code INSERT … ON DUPLICATE KEY UPDATE}로 쓴다. 같은 회원의 행동 두 개가 동시에 처리돼도 유니크 키 충돌 없이 마지막 계산이 남는다.
 * 그래서 엔티티에는 생성 메서드가 없다 (조회와 테이블 생성에만 쓴다).
 */
@Getter
@Entity
@Table(name = "preference_profiles", uniqueConstraints = @UniqueConstraint(
        name = "uk_preference_profiles_user_id_profile_key_lcls2", columnNames = {"user_id", "profile_key", "lcls2"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PreferenceProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    // FK(users.id)를 만들기 위한 읽기 전용 연관. 값은 위 userId로 쓴다
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_preference_profiles_user_id"))
    private User user;

    @Column(name = "profile_key", nullable = false, length = 20)
    private String profileKey;

    @Column(nullable = false, length = 6)
    private String lcls2;

    // Σ 가중치 × 0.5^(경과일/30). 불만족 신호가 많으면 음수가 될 수 있다
    @Column(nullable = false, precision = 10, scale = 4)
    private BigDecimal score;

    // 이 분류에 들어간 행동 수
    @Column(name = "signal_count", nullable = false)
    private int signalCount;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
