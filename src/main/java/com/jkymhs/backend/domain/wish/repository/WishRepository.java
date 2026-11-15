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

    /**
     * 새로 찜했으면 1, 이미 찜한 콘텐츠면 0 (유니크 키에 걸려 무시된다).
     *
     * saveAndFlush 후 DataIntegrityViolationException을 잡는 방식은 쓰지 않는다. 저장소 메서드에서 예외가 나면
     * 바깥 트랜잭션이 rollback-only로 표시돼서, 예외를 잡아도 커밋할 때 UnexpectedRollbackException이 난다.
     */
    @Modifying
    @Query(value = "INSERT IGNORE INTO wishlists (user_id, content_id, created_at) VALUES (:userId, :contentId, :now)",
            nativeQuery = true)
    int insertIgnore(@Param("userId") Long userId, @Param("contentId") Long contentId, @Param("now") LocalDateTime now);

    /** 지웠으면 1, 찜하지 않은 콘텐츠면 0 */
    @Modifying
    @Query("delete from Wish w where w.userId = :userId and w.contentId = :contentId")
    int deleteByUserIdAndContentId(@Param("userId") Long userId, @Param("contentId") Long contentId);

    // 찜 목록. idx_wishlists_user_id_created_at 순서 그대로 읽는다
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
