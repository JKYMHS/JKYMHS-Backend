package com.jkymhs.backend.domain.content.entity;

import com.jkymhs.backend.domain.category.entity.Category;
import com.jkymhs.backend.domain.region.entity.Region;
import com.jkymhs.backend.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Getter
@Entity
@Table(name = "contents", indexes = {
        @Index(name = "idx_contents_type_sido_dates",
                columnList = "content_type, is_deleted, sido_region_id, event_end_date, event_start_date, id"),
        @Index(name = "idx_contents_type_region", columnList = "content_type, is_deleted, region_id"),
        @Index(name = "idx_contents_type_popularity", columnList = "content_type, is_deleted, popularity_score, name, id"),
        @Index(name = "idx_contents_type_name", columnList = "content_type, is_deleted, name, id"),
        @Index(name = "idx_contents_type_lat_lng", columnList = "content_type, latitude, longitude"),
        @Index(name = "idx_contents_lcls3", columnList = "lcls3")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Content extends BaseTimeEntity {

    @Id
    private Long id;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "content_type", nullable = false, length = 20)
    private ContentType contentType;

    @Column(name = "tour_content_type_id", nullable = false)
    private int tourContentTypeId;

    @Column(nullable = false, length = 200)
    private String name;

    private String address;

    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(name = "sido_region_id")
    private Long sidoRegionId;

    @Column(name = "region_id")
    private Long regionId;

    @Getter(AccessLevel.NONE)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sido_region_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_contents_sido_region_id"))
    private Region sidoRegion;

    @Getter(AccessLevel.NONE)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "region_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_contents_region_id"))
    private Region region;

    @Column(length = 5)
    private String lcls1;

    @Column(length = 6)
    private String lcls2;

    @Column(length = 10)
    private String lcls3;

    @Column(name = "category_id")
    private Long categoryId;

    @Getter(AccessLevel.NONE)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_contents_category_id"))
    private Category category;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "io_type", length = 10)
    private IoType ioType;

    @Column(name = "thumbnail_url", length = 500)
    private String thumbnailUrl;

    @Column(length = 100)
    private String tel;

    @Column(name = "event_start_date")
    private LocalDate eventStartDate;

    @Column(name = "event_end_date")
    private LocalDate eventEndDate;

    @Column(name = "event_start_time")
    private LocalTime eventStartTime;

    @Column(name = "event_end_time")
    private LocalTime eventEndTime;

    @Column(name = "event_place")
    private String eventPlace;

    @Column(name = "playtime_raw", length = 500)
    private String playtimeRaw;

    @Column(name = "open_time")
    private LocalTime openTime;

    @Column(name = "close_time")
    private LocalTime closeTime;

    @Column(name = "last_entry_time")
    private LocalTime lastEntryTime;

    @Column(name = "operating_hours_raw", length = 500)
    private String operatingHoursRaw;

    @Column(name = "rest_days", length = 30)
    private String restDays;

    @Column(name = "rest_date_raw", length = 500)
    private String restDateRaw;

    @Column(name = "pet_allowed", columnDefinition = "tinyint(1)")
    private Boolean petAllowed;

    @Column(name = "intro_fetched_at")
    private LocalDateTime introFetchedAt;

    @Column(name = "popularity_score", nullable = false)
    private int popularityScore;

    @Column(name = "wish_count", nullable = false)
    private int wishCount;

    @Column(name = "tour_modified_at")
    private LocalDateTime tourModifiedAt;

    @Column(name = "is_deleted", nullable = false, columnDefinition = "tinyint(1)")
    private boolean deleted;
}
