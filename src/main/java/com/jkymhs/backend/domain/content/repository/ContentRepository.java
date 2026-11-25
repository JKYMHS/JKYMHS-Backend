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
