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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_preference_profiles_user_id"))
    private User user;

    @Column(name = "profile_key", nullable = false, length = 20)
    private String profileKey;

    @Column(nullable = false, length = 6)
    private String lcls2;

    @Column(nullable = false, precision = 10, scale = 4)
    private BigDecimal score;

    @Column(name = "signal_count", nullable = false)
    private int signalCount;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
