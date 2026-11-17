package com.jkymhs.backend.domain.user.repository;

import com.jkymhs.backend.domain.user.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    Optional<User> findByGoogleSub(String googleSub);

    /*
     * SELECT ... FOR UPDATE. 같은 회원의 요청 두 개가 동시에 와도 하나씩 처리되게 한다.
     * 예: 취향 저장이 동시에 두 번 오면 둘 다 "취향 행 없음"을 보고 INSERT해서 UNIQUE 위반(500)이 나고,
     * AI 추천 요청이 동시에 오면 둘 다 "오늘 9회 사용"을 보고 11번째 추천이 만들어진다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :userId")
    Optional<User> findByIdForUpdate(Long userId);
}
