package com.jkymhs.backend.domain.recommendation.entity;

import com.jkymhs.backend.domain.region.entity.Region;
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

/**
 * 추천 요청에서 고른 시·군·구. 없으면 시·도 전체다.
 */
@Getter
@Entity
@Table(name = "recommendation_request_regions", uniqueConstraints = @UniqueConstraint(
        name = "uk_recommendation_request_regions_request_id_region_id", columnNames = {"request_id", "region_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecommendationRequestRegion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_id", nullable = false)
    private Long requestId;

    @Getter(AccessLevel.NONE)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "request_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_recommendation_request_regions_request_id"))
    private RecommendationRequest request;

    @Column(name = "region_id", nullable = false)
    private Long regionId;

    @Getter(AccessLevel.NONE)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "region_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_recommendation_request_regions_region_id"))
    private Region region;

    private RecommendationRequestRegion(Long requestId, Long regionId) {
        this.requestId = requestId;
        this.regionId = regionId;
    }

    public static RecommendationRequestRegion of(Long requestId, Long regionId) {
        return new RecommendationRequestRegion(requestId, regionId);
    }
}
