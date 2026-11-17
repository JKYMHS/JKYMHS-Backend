package com.jkymhs.backend.domain.content.repository;

import com.jkymhs.backend.domain.content.entity.Content;
import com.jkymhs.backend.domain.content.entity.ContentType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ContentRepository extends JpaRepository<Content, Long> {

    Optional<Content> findByIdAndDeletedFalse(Long id);

    /**
     * AI 추천 후보: 관광지 전부 + 기간이 [from, to]와 겹치는 축제. 겹침 조건은 "시작 ≤ to 그리고 끝 ≥ from"이다.
     * 지역은 시·군·구 id(region_id)나 시·도 id(sido_region_id) 중 하나에 맞으면 된다.
     */
    @Query("""
            select c from Content c
            where c.deleted = false
              and (c.contentType = com.jkymhs.backend.domain.content.entity.ContentType.ATTRACTION
                   or (c.contentType = com.jkymhs.backend.domain.content.entity.ContentType.FESTIVAL
                       and c.eventStartDate <= :to and c.eventEndDate >= :from))
              and (c.regionId in :districtIds or c.sidoRegionId in :sidoIds)
            order by c.popularityScore desc, c.id
            """)
    List<Content> findCandidatePool(Collection<Long> districtIds, Collection<Long> sidoIds,
                                    LocalDate from, LocalDate to, Pageable pageable);

    /**
     * 운영 시간을 새로 받을 콘텐츠: 한 번도 안 받았거나 기준 시각보다 오래된 것, 오래된 순 (안 받은 것이 먼저).
     */
    @Query("""
            select c from Content c
            where c.deleted = false and c.contentType in :types
              and (c.introFetchedAt is null or c.introFetchedAt < :before)
            order by c.introFetchedAt asc nulls first, c.id
            """)
    List<Content> findIntroTargets(Collection<ContentType> types, LocalDateTime before, Pageable pageable);

    @Query("select max(c.tourModifiedAt) from Content c")
    Optional<LocalDateTime> findMaxTourModifiedAt();
}
