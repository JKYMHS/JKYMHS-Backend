package com.jkymhs.backend.domain.trip.repository;

import com.jkymhs.backend.domain.trip.entity.VisitReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

// 벌크 삭제 후 영속성 컨텍스트를 비우지 않는다 (같은 트랜잭션에서 잠근 여행·일정안 엔티티를 계속 고쳐야 해서). 지운 행의 엔티티는 다시 쓰지 않는다
public interface VisitReviewRepository extends JpaRepository<VisitReview, Long> {

    List<VisitReview> findAllByPlanItemIdIn(Collection<Long> planItemIds);

    long countByPlanItemIdIn(Collection<Long> planItemIds);

    // 일정 항목을 지우기 전에 먼저 지운다 (visit_reviews.plan_item_id FK)
    @Modifying(flushAutomatically = true)
    @Query("delete from VisitReview r where r.planItemId in :planItemIds")
    int deleteAllByPlanItemIdIn(Collection<Long> planItemIds);

    @Modifying(flushAutomatically = true)
    @Query("delete from VisitReview r where r.planItemId in (select i.id from PlanItem i where i.planId in :planIds)")
    int deleteAllByPlanIdIn(Collection<Long> planIds);

    @Modifying(flushAutomatically = true)
    @Query("delete from VisitReview r where r.userId = :userId")
    int deleteAllByUserId(Long userId);
}
