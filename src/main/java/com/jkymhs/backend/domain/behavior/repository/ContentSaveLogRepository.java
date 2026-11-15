package com.jkymhs.backend.domain.behavior.repository;

import com.jkymhs.backend.domain.behavior.entity.ContentSaveLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ContentSaveLogRepository extends JpaRepository<ContentSaveLog, Long> {

    @Query("select new com.jkymhs.backend.domain.behavior.repository.ContentCount(s.contentId, count(s)) "
            + "from ContentSaveLog s where s.createdAt >= :since group by s.contentId")
    List<ContentCount> countByContentSince(@Param("since") LocalDateTime since);

    @Modifying
    @Query("delete from ContentSaveLog s where s.userId = :userId")
    int deleteAllByUserId(@Param("userId") Long userId);
}
