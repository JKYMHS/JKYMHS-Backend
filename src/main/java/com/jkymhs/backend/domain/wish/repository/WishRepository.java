package com.jkymhs.backend.domain.wish.repository;

import com.jkymhs.backend.domain.wish.entity.Wish;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface WishRepository extends JpaRepository<Wish, Long> {
    @Modifying
    @Query(value = "INSERT IGNORE INTO wishlists (user_id, content_id, created_at) VALUES (:userId, :contentId, :now)",
            nativeQuery = true)
    int insertIgnore(@Param("userId") Long userId, @Param("contentId") Long contentId, @Param("now") LocalDateTime now);

    @Modifying
    @Query("delete from Wish w where w.userId = :userId and w.contentId = :contentId")
    int deleteByUserIdAndContentId(@Param("userId") Long userId, @Param("contentId") Long contentId);

    List<Wish> findAllByUserIdOrderByCreatedAtDescIdDesc(Long userId);

    @Query("select w.contentId from Wish w where w.userId = :userId and w.contentId in :contentIds")
    Set<Long> findContentIds(@Param("userId") Long userId, @Param("contentIds") Collection<Long> contentIds);

    long countByUserId(Long userId);

    @Query("select new com.jkymhs.backend.domain.wish.repository.ContentWishCount(w.contentId, count(w)) "
            + "from Wish w where w.createdAt >= :since group by w.contentId")
    List<ContentWishCount> countByContentSince(@Param("since") LocalDateTime since);

    @Modifying
    @Query("delete from Wish w where w.userId = :userId")
    int deleteAllByUserId(@Param("userId") Long userId);
}
