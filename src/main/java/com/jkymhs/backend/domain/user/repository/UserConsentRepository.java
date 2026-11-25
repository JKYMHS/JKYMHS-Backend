package com.jkymhs.backend.domain.user.repository;

import com.jkymhs.backend.domain.user.entity.UserConsent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface UserConsentRepository extends JpaRepository<UserConsent, Long> {

    List<UserConsent> findAllByUserId(Long userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from UserConsent c where c.user.id = :userId")
    int deleteAllByUserId(Long userId);
}
