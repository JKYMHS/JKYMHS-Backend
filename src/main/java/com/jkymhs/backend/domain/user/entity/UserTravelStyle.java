package com.jkymhs.backend.domain.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Entity
@Table(name = "user_travel_styles", uniqueConstraints = {
        @UniqueConstraint(name = "uk_user_travel_styles_user_id_style_code", columnNames = {"user_id", "style_code"})
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserTravelStyle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "style_code", nullable = false, length = 20)
    private TravelStyle style;

    private UserTravelStyle(User user, TravelStyle style) {
        this.user = user;
        this.style = style;
    }

    public static UserTravelStyle create(User user, TravelStyle style) {
        return new UserTravelStyle(user, style);
    }
}
