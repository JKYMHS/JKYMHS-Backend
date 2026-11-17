package com.jkymhs.backend.domain.trip.repository;

import com.jkymhs.backend.domain.trip.entity.PlanItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

// 벌크 삭제 후 영속성 컨텍스트를 비우지 않는다 (같은 트랜잭션에서 잠근 여행·일정안 엔티티를 계속 고쳐야 해서). 지운 행의 엔티티는 다시 쓰지 않는다
public interface PlanItemRepository extends JpaRepository<PlanItem, Long> {

    List<PlanItem> findAllByPlanIdOrderByDayNumberAscSortOrderAscIdAsc(Long planId);

    List<PlanItem> findAllByPlanIdInOrderByPlanIdAscDayNumberAscSortOrderAscIdAsc(Collection<Long> planIds);

    long countByPlanId(Long planId);

    // 일정안별 장소 수 (목록·상세 화면). [planId, count]
    @Query("select i.planId, count(i) from PlanItem i where i.planId in :planIds group by i.planId")
    List<Object[]> countByPlanIds(Collection<Long> planIds);

    // 일정안이 여행 기간보다 길어지는지 확인 (여행 기간 줄이기)
    @Query("select i from PlanItem i where i.planId in :planIds and i.dayNumber > :maxDay")
    List<PlanItem> findAllByPlanIdInAndDayNumberGreaterThan(Collection<Long> planIds, int maxDay);

    // 캘린더에 올라간 항목 (일정 삭제 시 캘린더 일정도 지우라고 알릴 때)
    List<PlanItem> findAllByPlanIdInAndCalendarEventIdIsNotNull(Collection<Long> planIds);

    @Modifying(flushAutomatically = true)
    @Query("delete from PlanItem i where i.planId in :planIds")
    int deleteAllByPlanIdIn(Collection<Long> planIds);

    @Modifying(flushAutomatically = true)
    @Query("delete from PlanItem i where i.id in :ids")
    int deleteAllByIdIn(Collection<Long> ids);
}
