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

/**
 * 찜 하나 (회원 × 콘텐츠). 같은 콘텐츠를 두 번 찜할 수 없게 (user_id, content_id) 유니크 키를 둔다.
 *
 * 행은 {@code INSERT IGNORE}로만 넣는다 ({@link com.jkymhs.backend.domain.wish.repository.WishRepository#insertIgnore}).
 * 이미 찜했으면 유니크 키에 걸려 0행이 되므로 "새로 찜했는지"를 한 문장으로 알 수 있다. 그래서 엔티티에는 생성 메서드가 없다.
 *
 * 찜 목록은 "회원의 찜을 최근 순으로"(created_at desc, id desc) 읽으므로 (user_id, created_at, id) 인덱스를 둔다.
 */
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

    /*
     * 아래 두 연관은 FK(users.id, contents.id)를 만들기 위한 읽기 전용 매핑이다. 값은 userId·contentId 숫자 컬럼으로 쓴다.
     * 회원·콘텐츠 엔티티를 읽지 않고 id만으로 저장·조회하려는 것이다 (콘텐츠 정보는 ContentService가 한 번에 준다).
     */
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

    // 찜한 시각 (찜 목록 정렬·인기 점수 30일 집계). created_at만 있어서 BaseTimeEntity를 상속하지 않는다
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
