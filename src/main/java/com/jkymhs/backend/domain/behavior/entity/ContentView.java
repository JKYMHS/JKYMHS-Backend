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

/**
 * 로그인한 회원이 콘텐츠 상세를 본 기록. 회원·콘텐츠·날짜마다 한 행이라 같은 날 여러 번 열어도 한 번만 센다
 * (새로고침을 반복해서 인기 점수를 올리지 못하게).
 *
 * 행은 {@code INSERT IGNORE}로만 넣는다 ({@link com.jkymhs.backend.domain.behavior.repository.ContentViewRepository#insertIgnore}).
 * 유니크 키에 걸리면 0행이 되므로 "오늘 처음 본 것인지"를 한 문장으로 알 수 있다. 그래서 엔티티에는 생성 메서드가 없다.
 *
 * content_id에는 FK를 걸지 않는다 (ERD 그대로). 조회 기록은 통계용이라 콘텐츠가 지워져도 남아 있어도 된다.
 * 인기 점수 배치가 날짜 범위로 세므로 view_date에 인덱스를 둔다.
 */
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

    // FK(users.id)를 만들기 위한 읽기 전용 연관. 값은 위 userId로 쓴다 (회원 엔티티를 읽지 않고 id만으로 저장하려고)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_content_views_user_id"))
    private User user;

    @Column(name = "content_id", nullable = false)
    private Long contentId;

    // KST 날짜
    @Column(name = "view_date", nullable = false)
    private LocalDate viewDate;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
