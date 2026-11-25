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
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "content_views",
        uniqueConstraints = @UniqueConstraint(name = "uk_content_views_user_id_content_id_view_date",
                columnNames = {"user_id", "content_id", "view_date"}),
        indexes = @Index(name = "idx_content_views_view_date", columnList = "view_date"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContentView {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_content_views_user_id"))
    private User user;

    @Column(name = "content_id", nullable = false)
    private Long contentId;

    @Column(name = "view_date", nullable = false)
    private LocalDate viewDate;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
