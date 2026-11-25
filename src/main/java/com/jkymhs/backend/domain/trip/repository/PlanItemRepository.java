package com.jkymhs.backend.domain.trip.repository;

import com.jkymhs.backend.domain.trip.entity.PlanItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

// 벌크 삭제 후 영속성 컨텍스트를 비우지 않음 (엔티티 계속 사용)
public interface PlanItemRepository extends JpaRepository<PlanItem, Long> {
    List<PlanItem> findAllByPlanIdOrderByDayNumberAscSortOrderAscIdAsc(Long planId);

    List<PlanItem> findAllByPlanIdInOrderByPlanIdAscDayNumberAscSortOrderAscIdAsc(Collection<Long> planIds);

    long countByPlanId(Long planId);

    @Query("select i.planId, count(i) from PlanItem i where i.planId in :planIds group by i.planId")
    List<Object[]> countByPlanIds(Collection<Long> planIds);

    @Query("select i from PlanItem i where i.planId in :planIds and i.dayNumber > :maxDay")
    List<PlanItem> findAllByPlanIdInAndDayNumberGreaterThan(Collection<Long> planIds, int maxDay);

    List<PlanItem> findAllByPlanIdInAndCalendarEventIdIsNotNull(Collection<Long> planIds);

    @Modifying(flushAutomatically = true)
    @Query("delete from PlanItem i where i.planId in :planIds")
    int deleteAllByPlanIdIn(Collection<Long> planIds);

    @Modifying(flushAutomatically = true)
    @Query("delete from PlanItem i where i.id in :ids")
    int deleteAllByIdIn(Collection<Long> ids);
}
