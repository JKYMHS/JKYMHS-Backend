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

    /**
     * 오늘 처음 본 것이면 1, 이미 본 날이면 0 (유니크 키에 걸려 무시된다).
     * "있는지 보고 없으면 저장"을 두 번에 나눠 하면 동시에 두 번 열었을 때 둘 다 저장하려다 한쪽이 실패한다.
     */
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
