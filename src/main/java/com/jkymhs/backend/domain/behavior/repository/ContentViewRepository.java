package com.jkymhs.backend.domain.behavior.repository;

import com.jkymhs.backend.domain.behavior.entity.ContentView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface ContentViewRepository extends JpaRepository<ContentView, Long> {

    @Modifying
    @Query(value = "INSERT IGNORE INTO content_views (user_id, content_id, view_date, created_at) "
            + "VALUES (:userId, :contentId, :viewDate, :now)", nativeQuery = true)
    int insertIgnore(@Param("userId") Long userId, @Param("contentId") Long contentId,
                     @Param("viewDate") LocalDate viewDate, @Param("now") LocalDateTime now);

    @Query("select new com.jkymhs.backend.domain.behavior.repository.ContentCount(v.contentId, count(v)) "
            + "from ContentView v where v.viewDate >= :since group by v.contentId")
    List<ContentCount> countByContentSince(@Param("since") LocalDate since);

    @Modifying
    @Query("delete from ContentView v where v.userId = :userId")
    int deleteAllByUserId(@Param("userId") Long userId);
}
