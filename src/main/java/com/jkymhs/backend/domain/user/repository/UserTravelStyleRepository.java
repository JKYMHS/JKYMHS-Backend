package com.jkymhs.backend.domain.user.repository;

import com.jkymhs.backend.domain.user.entity.UserTravelStyle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface UserTravelStyleRepository extends JpaRepository<UserTravelStyle, Long> {

    List<UserTravelStyle> findAllByUserIdOrderByIdAsc(Long userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from UserTravelStyle s where s.user.id = :userId")
    int deleteAllByUserId(Long userId);
}
