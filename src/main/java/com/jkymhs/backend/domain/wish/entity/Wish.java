package com.jkymhs.backend.domain.wish.entity;

import com.jkymhs.backend.domain.content.entity.Content;
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

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "wishlists",
        uniqueConstraints = @UniqueConstraint(name = "uk_wishlists_user_id_content_id",
                columnNames = {"user_id", "content_id"}),
        indexes = @Index(name = "idx_wishlists_user_id_created_at", columnList = "user_id, created_at, id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Wish {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_wishlists_user_id"))
    private User user;

    @Column(name = "content_id", nullable = false)
    private Long contentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "content_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_wishlists_content_id"))
    private Content content;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
