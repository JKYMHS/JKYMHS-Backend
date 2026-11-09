package com.jkymhs.backend.domain.trip.entity;

import com.jkymhs.backend.domain.user.entity.User;
import com.jkymhs.backend.global.entity.BaseTimeEntity;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 끝난 여행의 장소 하나에 대한 방문 확인 (갔는지, 만족도). 일정 항목 하나에 하나만 있다 (UK plan_item_id).
 */
@Getter
@Entity
@Table(name = "visit_reviews", uniqueConstraints = @UniqueConstraint(
        name = "uk_visit_reviews_plan_item_id", columnNames = "plan_item_id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VisitReview extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Getter(AccessLevel.NONE)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_visit_reviews_user_id"))
    private User user;

    @Column(name = "plan_item_id", nullable = false)
    private Long planItemId;

    @Getter(AccessLevel.NONE)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_item_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_visit_reviews_plan_item_id"))
    private PlanItem planItem;

    @Column(nullable = false)
    private boolean visited;

    // 1~5. 가지 않았으면 null
    @JdbcTypeCode(SqlTypes.TINYINT)
    private Integer rating;

    private VisitReview(Long userId, Long planItemId, boolean visited, Integer rating) {
        this.userId = userId;
        this.planItemId = planItemId;
        answer(visited, rating);
    }

    public static VisitReview create(Long userId, Long planItemId, boolean visited, Integer rating) {
        return new VisitReview(userId, planItemId, visited, rating);
    }

    public void answer(boolean visited, Integer rating) {
        this.visited = visited;
        this.rating = visited ? rating : null;
    }
}
