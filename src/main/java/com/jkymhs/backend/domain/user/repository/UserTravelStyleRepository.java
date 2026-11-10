package com.jkymhs.backend.domain.user.repository;

import com.jkymhs.backend.domain.user.entity.UserTravelStyle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface UserTravelStyleRepository extends JpaRepository<UserTravelStyle, Long> {

    // 저장 순서(id)대로 돌려줘서 FE가 고른 순서가 유지되게 한다
    List<UserTravelStyle> findAllByUserIdOrderByIdAsc(Long userId);

    /*
     * 취향을 다시 저장할 때 기존 스타일을 먼저 지우고 새로 넣는다.
     * JPQL DELETE는 바로 DB에 실행돼서, 뒤이은 INSERT가 (user_id, style_code) UNIQUE에 걸리지 않는다.
     * (엔티티 delete() + save()는 Hibernate가 INSERT를 DELETE보다 먼저 flush해서 같은 스타일을 다시 고르면 UNIQUE 위반이 난다)
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from UserTravelStyle s where s.user.id = :userId")
    int deleteAllByUserId(Long userId);
}
