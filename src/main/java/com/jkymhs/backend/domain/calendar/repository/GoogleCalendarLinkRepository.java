package com.jkymhs.backend.domain.calendar.repository;

import com.jkymhs.backend.domain.calendar.entity.GoogleCalendarLink;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface GoogleCalendarLinkRepository extends JpaRepository<GoogleCalendarLink, Long> {
    Optional<GoogleCalendarLink> findByUserId(Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from GoogleCalendarLink l where l.userId = :userId")
    Optional<GoogleCalendarLink> findByUserIdForUpdate(Long userId);

    @Modifying(flushAutomatically = true)
    @Query("delete from GoogleCalendarLink l where l.userId = :userId")
    int deleteByUserIdInBulk(Long userId);
}
