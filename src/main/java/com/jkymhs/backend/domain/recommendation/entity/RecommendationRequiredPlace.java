package com.jkymhs.backend.domain.recommendation.entity;

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

@Getter
@Entity
@Table(name = "recommendation_required_places", uniqueConstraints = @UniqueConstraint(
        name = "uk_recommendation_required_places_request_id_content_id", columnNames = {"request_id", "content_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecommendationRequiredPlace {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_id", nullable = false)
    private Long requestId;

    @Getter(AccessLevel.NONE)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "request_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_recommendation_required_places_request_id"))
    private RecommendationRequest request;

    @Column(name = "content_id", nullable = false)
    private Long contentId;

    @Column(nullable = false)
    private int sortOrder;

    private RecommendationRequiredPlace(Long requestId, Long contentId, int sortOrder) {
        this.requestId = requestId;
        this.contentId = contentId;
        this.sortOrder = sortOrder;
    }

    public static RecommendationRequiredPlace of(Long requestId, Long contentId, int sortOrder) {
        return new RecommendationRequiredPlace(requestId, contentId, sortOrder);
    }
}
