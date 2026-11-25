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

    @Column(name = "accessible_only", nullable = false)
    private boolean accessible;

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

    public void update(Pace pace, TimeOfDay timeOfDay, boolean accessible, boolean pet) {
        this.pace = pace;
        this.timeOfDay = timeOfDay;
        this.accessible = accessible;
        this.pet = pet;
    }
}
