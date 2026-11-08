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

/**
 * 관광지·축제·음식점·숙박 한 건. PK는 TourAPI contentid를 그대로 쓴다 (자체 id를 따로 두면 동기화 때마다 contentid → id 변환이 필요하다).
 *
 * 쓰기는 모두 {@code ContentService}가 JdbcTemplate로 한다 (CSV 3만 건 적재, 매일 동기화 1000건 단위 upsert).
 * JPA로 한 건씩 저장하면 3만 번 SELECT + INSERT가 나가서 수 분이 걸린다. 그래서 이 엔티티에는 상태를 바꾸는 메서드가 없고 조회에만 쓴다.
 *
 * 인덱스는 탐색 화면 조회 조건 순서에 맞췄다 (항상 content_type + is_deleted로 먼저 좁힌다).
 * - type_sido_dates: 축제 탭 "지역 + 기간" 필터와 최신순(시작일) 정렬
 * - type_region: 시·군·구 필터, 지역별 개수
 * - type_popularity / type_name: 인기순·이름순 커서 목록 ((정렬값, id) > 커서 조건으로 인덱스만 읽는다)
 * - type_lat_lng: 지도 화면 bbox(위도·경도 범위) 조회, 주변 음식점·숙박
 * - lcls3: 칩·카테고리(분류 코드) 필터
 */
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

    // TourAPI contentid
    @Id
    private Long id;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "content_type", nullable = false, length = 20)
    private ContentType contentType;

    // TourAPI contentTypeId (12 관광지, 14 문화시설, 15 축제, 32 숙박, 39 음식점). detailIntro2 호출에 필요하다
    @Column(name = "tour_content_type_id", nullable = false)
    private int tourContentTypeId;

    @Column(nullable = false, length = 200)
    private String name;

    private String address;

    // TourAPI mapy(위도)·mapx(경도). 소수 7자리면 약 1cm 단위라 충분하다
    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal longitude;

    // 지역 id는 값으로 읽고, 아래 연관관계는 FK 제약을 만들기 위해서만 둔다 (insertable/updatable=false)
    @Column(name = "sido_region_id")
    private Long sidoRegionId;

    // 시·군·구 id. 시·군·구가 없는 지역(세종)이면 시·도 id
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

    // 신 분류 대·중·소 ("VE", "VE07", "VE070100")
    @Column(length = 5)
    private String lcls1;

    @Column(length = 6)
    private String lcls2;

    @Column(length = 10)
    private String lcls3;

    // 탐색 필터 카테고리 (적재할 때 분류 코드로 정한다). 음식점·숙박은 null
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

    // 축제 기간. 날짜가 없는 축제는 적재하지 않는다 (기능 명세서 006-01)
    @Column(name = "event_start_date")
    private LocalDate eventStartDate;

    @Column(name = "event_end_date")
    private LocalDate eventEndDate;

    // 축제 공연 시간 (playtime의 첫 "HH:mm~HH:mm")
    @Column(name = "event_start_time")
    private LocalTime eventStartTime;

    @Column(name = "event_end_time")
    private LocalTime eventEndTime;

    @Column(name = "event_place")
    private String eventPlace;

    @Column(name = "playtime_raw", length = 500)
    private String playtimeRaw;

    // 운영 시간 (usetime의 첫 "HH:mm~HH:mm"). 밤을 넘기면 close가 open보다 이르다
    @Column(name = "open_time")
    private LocalTime openTime;

    @Column(name = "close_time")
    private LocalTime closeTime;

    @Column(name = "last_entry_time")
    private LocalTime lastEntryTime;

    // 원문은 형식이 제각각이라 상세 화면에 그대로 보여주려고 남긴다
    @Column(name = "operating_hours_raw", length = 500)
    private String operatingHoursRaw;

    // "MON,TUE" / "NONE"(연중무휴) / null(모름). RestDays가 바꾼다
    @Column(name = "rest_days", length = 30)
    private String restDays;

    @Column(name = "rest_date_raw", length = 500)
    private String restDateRaw;

    // 반려동물 동반 가능 여부 (detailPetTour2). null = 모름
    @Column(name = "pet_allowed", columnDefinition = "tinyint(1)")
    private Boolean petAllowed;

    // detailIntro2(운영 시간·쉬는 날)를 마지막으로 받은 시각. 30일 지나면 동기화 배치가 다시 받는다
    @Column(name = "intro_fetched_at")
    private LocalDateTime introFetchedAt;

    // 0~1000, 인기 점수 배치(D3)가 쓴다
    @Column(name = "popularity_score", nullable = false)
    private int popularityScore;

    @Column(name = "wish_count", nullable = false)
    private int wishCount;

    @Column(name = "tour_modified_at")
    private LocalDateTime tourModifiedAt;

    // TourAPI에서 내려간(showflag=0) 콘텐츠. 지우지 않는 이유: 찜·일정에 남아 있는 콘텐츠가 "삭제된 장소"로 보여야 한다
    @Column(name = "is_deleted", nullable = false, columnDefinition = "tinyint(1)")
    private boolean deleted;
}
