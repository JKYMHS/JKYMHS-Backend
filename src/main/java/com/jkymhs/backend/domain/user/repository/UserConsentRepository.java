package com.jkymhs.backend.domain.user.repository;

import com.jkymhs.backend.domain.user.entity.UserConsent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface UserConsentRepository extends JpaRepository<UserConsent, Long> {

    List<UserConsent> findAllByUserId(Long userId);

    // 탈퇴할 때 DELETE 한 번으로 지운다 (행마다 SELECT 후 DELETE하지 않게)
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from UserConsent c where c.user.id = :userId")
    int deleteAllByUserId(Long userId);
}
