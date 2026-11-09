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

    /*
     * 업로드·일괄 업로드·실패만 다시: "진행 중인 작업이 있으면 409"를 확인하고 작업을 넣는 사이에 같은 회원의 두 요청이 끼어들지 않게,
     * 회원당 하나뿐인 연동 행을 잠가서 한 줄로 세운다 (작업 테이블에는 잠글 행이 아직 없다).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from GoogleCalendarLink l where l.userId = :userId")
    Optional<GoogleCalendarLink> findByUserIdForUpdate(Long userId);

    // 탈퇴: 회원 행을 지우기 전에 FK가 걸린 연동 행을 지운다. 엔티티 remove 대신 DELETE 문 (TripWithdrawListener와 같은 이유)
    @Modifying(flushAutomatically = true)
    @Query("delete from GoogleCalendarLink l where l.userId = :userId")
    int deleteByUserIdInBulk(Long userId);
}
