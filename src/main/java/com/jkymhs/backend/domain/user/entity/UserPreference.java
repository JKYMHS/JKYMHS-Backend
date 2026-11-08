package com.jkymhs.backend.domain.user.entity;

import com.jkymhs.backend.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 회원 한 명의 취향(여행 속도·시작 시간대·이동 편의·반려동물). 회원마다 최대 한 행이다.
 * 여행 스타일은 여러 개라서 {@link UserTravelStyle}에 따로 저장한다.
 *
 * 온보딩을 건너뛴 회원은 이 행이 없다. 기본값(RELAXED·MORNING 등)을 저장하지 않고 쓸 때 채운다
 * (PreferenceService.getEffective). 저장해 두면 "직접 고른 값"과 "기본값"을 구분할 수 없기 때문이다.
 */
@Getter
@Entity
@Table(name = "user_preferences", uniqueConstraints = {
        @UniqueConstraint(name = "uk_user_preferences_user_id", columnNames = "user_id")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserPreference extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 회원당 한 행이라 1:1. user_id UNIQUE가 "한 행"을 DB에서도 보장한다
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private Pace pace;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private TimeOfDay timeOfDay;

    // 유모차·휠체어·오래 걷기 어려움을 하나로 묶은 값 (FE가 한 체크박스로 받는다)
    // ACCESSIBLE은 MySQL 예약어라서 컬럼 이름으로 쓰면 CREATE TABLE이 문법 오류로 실패한다. 그래서 컬럼 이름만 바꿨다
    @Column(name = "accessible_only", nullable = false)
    private boolean accessible;

    // 반려동물 동반 가능 장소만 추천
    @Column(nullable = false)
    private boolean pet;

    private UserPreference(User user, Pace pace, TimeOfDay timeOfDay, boolean accessible, boolean pet) {
        this.user = user;
        this.pace = pace;
        this.timeOfDay = timeOfDay;
        this.accessible = accessible;
        this.pet = pet;
    }

    public static UserPreference create(User user, Pace pace, TimeOfDay timeOfDay, boolean accessible, boolean pet) {
        return new UserPreference(user, pace, timeOfDay, accessible, pet);
    }

    // 마이페이지에서 다시 저장하면 전체를 바꾼다 (PUT)
    public void update(Pace pace, TimeOfDay timeOfDay, boolean accessible, boolean pet) {
        this.pace = pace;
        this.timeOfDay = timeOfDay;
        this.accessible = accessible;
        this.pet = pet;
    }
}
