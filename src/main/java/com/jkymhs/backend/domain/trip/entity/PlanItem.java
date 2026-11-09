package com.jkymhs.backend.domain.trip.entity;

import com.jkymhs.backend.domain.content.entity.Content;
import com.jkymhs.backend.domain.route.service.MoveMode;
import com.jkymhs.backend.domain.route.service.RouteResult;
import com.jkymhs.backend.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
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
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Objects;

/**
 * 일정안의 장소 하나 (몇째 날 몇 번째, 시간, 메모, 앞 장소에서 오는 이동).
 *
 * snapshot_* 컬럼: 담을 때의 장소 이름·주소·사진·좌표를 복사해 둔다. TourAPI에서 장소가 내려가도(소프트 삭제) 일정은 그대로 보여야 하고,
 * 일정 화면마다 contents를 다시 읽지 않아도 되게 하기 위해서다.
 *
 * 이동 컬럼(transport_mode …)은 "앞 항목(첫째 날 첫 항목은 출발지)에서 이 항목으로 오는" 이동이다.
 *
 * 캘린더 컬럼(calendar_event_id, calendar_id, sync_status, synced_at)은 캘린더 도메인이 아래 의도 메서드로만 바꾼다.
 * 구글 캘린더 eventId는 이 행의 id로 만들기 때문에, 전체 저장(PUT)에서 남아 있는 항목은 id를 유지한다.
 */
@Getter
@Entity
@Table(name = "plan_items", indexes = @Index(name = "idx_plan_items_plan_id_day_sort",
        columnList = "plan_id, day_number, sort_order"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlanItem extends BaseTimeEntity {

    public static final int MEMO_MAX_LENGTH = 200;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "plan_id", nullable = false)
    private Long planId;

    @Getter(AccessLevel.NONE)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_plan_items_plan_id"))
    private Plan plan;

    @Column(name = "content_id")
    private Long contentId;

    @Getter(AccessLevel.NONE)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "content_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_plan_items_content_id"))
    private Content content;

    @Column(nullable = false)
    private int dayNumber;

    // 그날 안에서의 순서. 저장할 때마다 1..n으로 다시 매긴다
    @Column(nullable = false)
    private int sortOrder;

    // null이면 캘린더에 종일 일정으로 올린다
    private LocalTime startTime;

    private LocalTime endTime;

    private Integer stayMinutes;

    @Column(length = MEMO_MAX_LENGTH)
    private String memo;

    @Column(nullable = false, length = 200)
    private String snapshotName;

    private String snapshotAddress;

    @Column(length = 500)
    private String snapshotImageUrl;

    @Column(length = 50)
    private String snapshotCategory;

    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal snapshotLat;

    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal snapshotLng;

    // AI 추천 이유 ("아이와 함께 걷기 좋은 궁궐")
    @Column(length = 500)
    private String aiReason;

    // AI 추천 요청에서 "꼭 갈 곳"으로 고른 장소
    @Column(name = "is_required", nullable = false)
    private boolean required;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 10)
    private MoveMode transportMode;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 10)
    private MoveMode altMode;

    private Integer travelMinutes;

    private Integer altTravelMinutes;

    private String routeSummary;

    @Column(columnDefinition = "mediumtext")
    private String routePolyline;

    @Column(name = "is_estimated", nullable = false)
    private boolean estimated;

    @Column(length = 100)
    private String calendarEventId;

    private String calendarId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 10)
    private CalendarSyncStatus syncStatus;

    private LocalDateTime syncedAt;

    private PlanItem(Long planId, Long contentId, int dayNumber, int sortOrder) {
        this.planId = planId;
        this.contentId = contentId;
        this.dayNumber = dayNumber;
        this.sortOrder = sortOrder;
        this.syncStatus = CalendarSyncStatus.NONE;
    }

    public static PlanItem create(Long planId, Long contentId, int dayNumber, int sortOrder, PlaceSnapshot place) {
        PlanItem item = new PlanItem(planId, contentId, dayNumber, sortOrder);
        item.applyPlace(contentId, place);
        return item;
    }

    /** 다른 일정안으로 복사. 캘린더 상태는 비운다 (새 항목이라 캘린더에 올린 적이 없다) */
    public PlanItem copyTo(Long planId) {
        PlanItem copy = new PlanItem(planId, contentId, dayNumber, sortOrder);
        copy.startTime = startTime;
        copy.endTime = endTime;
        copy.stayMinutes = stayMinutes;
        copy.memo = memo;
        copy.snapshotName = snapshotName;
        copy.snapshotAddress = snapshotAddress;
        copy.snapshotImageUrl = snapshotImageUrl;
        copy.snapshotCategory = snapshotCategory;
        copy.snapshotLat = snapshotLat;
        copy.snapshotLng = snapshotLng;
        copy.aiReason = aiReason;
        copy.required = required;
        copy.transportMode = transportMode;
        copy.altMode = altMode;
        copy.travelMinutes = travelMinutes;
        copy.altTravelMinutes = altTravelMinutes;
        copy.routeSummary = routeSummary;
        copy.routePolyline = routePolyline;
        copy.estimated = estimated;
        return copy;
    }

    public void applyPlace(Long contentId, PlaceSnapshot place) {
        this.contentId = contentId;
        this.snapshotName = place.name();
        this.snapshotAddress = place.address();
        this.snapshotImageUrl = place.imageUrl();
        this.snapshotCategory = place.category();
        this.snapshotLat = BigDecimal.valueOf(place.lat());
        this.snapshotLng = BigDecimal.valueOf(place.lng());
    }

    public void moveTo(int dayNumber, int sortOrder) {
        this.dayNumber = dayNumber;
        this.sortOrder = sortOrder;
    }

    public void changeSchedule(LocalTime startTime, LocalTime endTime, Integer stayMinutes, String memo) {
        this.startTime = startTime;
        this.endTime = endTime;
        this.stayMinutes = stayMinutes;
        this.memo = memo;
    }

    public void changeAiInfo(String aiReason, boolean required) {
        this.aiReason = aiReason;
        this.required = required;
    }

    /** 앞 항목에서 오는 이동. null이면 이동 정보를 지운다 (그날 첫 항목) */
    public void changeMove(RouteResult move) {
        if (move == null) {
            this.transportMode = null;
            this.altMode = null;
            this.travelMinutes = null;
            this.altTravelMinutes = null;
            this.routeSummary = null;
            this.routePolyline = null;
            this.estimated = false;
            return;
        }
        this.transportMode = move.mode();
        this.altMode = move.altMode();
        this.travelMinutes = move.minutes();
        this.altTravelMinutes = move.altMinutes();
        this.routeSummary = move.summary();
        this.routePolyline = move.polyline();
        this.estimated = move.estimated();
    }

    public double lat() {
        return snapshotLat.doubleValue();
    }

    public double lng() {
        return snapshotLng.doubleValue();
    }

    public int travelMinutesOrZero() {
        return travelMinutes == null ? 0 : travelMinutes;
    }

    /** 캘린더 일정에 영향을 주는 값(장소·날짜·순서·시간·메모)이 바뀌었는지 */
    public boolean differsForCalendar(Long contentId, int dayNumber, int sortOrder, LocalTime startTime,
                                      LocalTime endTime, String memo) {
        return !Objects.equals(this.contentId, contentId) || this.dayNumber != dayNumber
                || this.sortOrder != sortOrder || !Objects.equals(this.startTime, startTime)
                || !Objects.equals(this.endTime, endTime) || !Objects.equals(this.memo, memo);
    }

    public boolean hasCalendarEvent() {
        return calendarEventId != null;
    }

    // ---------- 캘린더 (캘린더 도메인이 부른다) ----------

    /** 캘린더에 올린 항목이 바뀌었다. 올린 적 없는 항목은 그대로 둔다 */
    public void markDirty() {
        if (syncStatus == CalendarSyncStatus.SYNCED) {
            this.syncStatus = CalendarSyncStatus.DIRTY;
        }
    }

    public void markPending() {
        this.syncStatus = CalendarSyncStatus.PENDING;
    }

    public void markSynced(String calendarId, String eventId, LocalDateTime now) {
        this.calendarId = calendarId;
        this.calendarEventId = eventId;
        this.syncStatus = CalendarSyncStatus.SYNCED;
        this.syncedAt = now;
    }

    public void markFailed() {
        this.syncStatus = CalendarSyncStatus.FAILED;
    }

    /** 캘린더에서 일정을 지웠거나 연동을 끊었다 */
    public void clearCalendar() {
        this.calendarId = null;
        this.calendarEventId = null;
        this.syncStatus = CalendarSyncStatus.NONE;
        this.syncedAt = null;
    }
}
