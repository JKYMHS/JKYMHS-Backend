package com.jkymhs.backend.domain.trip.entity;

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
 * 여행 지역 (여행 하나에 1~5개). 시·도나 시·군·구 id를 그대로 저장한다.
 */
@Getter
@Entity
@Table(name = "trip_regions", uniqueConstraints = @UniqueConstraint(
        name = "uk_trip_regions_trip_id_region_id", columnNames = {"trip_id", "region_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TripRegion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "trip_id", nullable = false)
    private Long tripId;

    @Getter(AccessLevel.NONE)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_trip_regions_trip_id"))
    private Trip trip;

    @Column(name = "region_id", nullable = false)
    private Long regionId;

    @Getter(AccessLevel.NONE)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "region_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_trip_regions_region_id"))
    private Region region;

    private TripRegion(Long tripId, Long regionId) {
        this.tripId = tripId;
        this.regionId = regionId;
    }

    public static TripRegion of(Long tripId, Long regionId) {
        return new TripRegion(tripId, regionId);
    }
}
