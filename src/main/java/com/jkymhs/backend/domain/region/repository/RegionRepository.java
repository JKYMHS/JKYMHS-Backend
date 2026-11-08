package com.jkymhs.backend.domain.region.repository;

import com.jkymhs.backend.domain.region.entity.Region;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

public interface RegionRepository extends JpaRepository<Region, Long> {

    // 부모 이름까지 쓰므로 부모를 같이 읽는다 (지역 수가 300개 미만이라 한 번에 읽어도 가볍다)
    @Query("select r from Region r left join fetch r.parent order by r.depth, r.sortOrder")
    List<Region> findAllWithParent();

    @Query("select r from Region r left join fetch r.parent where r.id in :ids")
    List<Region> findAllWithParentByIdIn(Collection<Long> ids);

    @Query("select r from Region r where r.parent.id = :parentId order by r.sortOrder")
    List<Region> findChildren(Long parentId);
}
