package com.jkymhs.backend.domain.content.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * 동기화 배치가 어디까지 했는지. 서버가 재시작돼도 이어서 하려고 Redis가 아니라 DB에 둔다.
 * updated_at만 있어서 BaseTimeEntity를 상속하지 않고, 시각은 Clock으로 받은 값을 직접 넣는다 (테스트에서 시계를 고정할 수 있게).
 */
@Getter
@Entity
@Table(name = "tour_sync_cursors")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TourSyncCursor {

    @Id
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 30)
    private TourSyncCursorName name;

    @Column(nullable = false, length = 50)
    private String value;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    private TourSyncCursor(TourSyncCursorName name, String value, LocalDateTime now) {
        this.name = name;
        this.value = value;
        this.updatedAt = now;
    }

    public static TourSyncCursor create(TourSyncCursorName name, String value, LocalDateTime now) {
        return new TourSyncCursor(name, value, now);
    }

    public void moveTo(String value, LocalDateTime now) {
        this.value = value;
        this.updatedAt = now;
    }
}
