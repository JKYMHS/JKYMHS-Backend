package com.jkymhs.backend.domain.auth.repository;

import com.jkymhs.backend.domain.auth.entity.RefreshToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /*
     * 재발급용 조회. SELECT ... FOR UPDATE로 행을 잠가서 트랜잭션이 끝날 때까지 같은 행을 읽으려는 다른 요청을 기다리게 한다.
     * 잠그지 않으면 같은 토큰으로 재발급 요청이 동시에 두 번 왔을 때 둘 다 "아직 폐기 안 됨"을 보고 새 토큰을 하나씩 받아간다.
     * 잠그면 두 번째 요청은 첫 번째가 폐기한 뒤의 상태를 읽어서 재사용으로 걸린다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RefreshToken r where r.tokenHash = :tokenHash")
    Optional<RefreshToken> findByTokenHashForUpdate(String tokenHash);

    /*
     * 그 회원의 아직 살아 있는 토큰을 UPDATE 한 번으로 모두 폐기한다 (탈취 의심 시 모든 기기 로그아웃).
     * 이미 폐기된 토큰은 건드리지 않아서 원래 폐기 시각이 남는다.
     * clearAutomatically: 이 UPDATE는 영속성 컨텍스트(1차 캐시)를 거치지 않고 DB에 바로 실행된다.
     * 캐시를 비워야 이후에 엔티티를 다시 읽을 때 바뀌기 전 값을 보지 않는다.
     */
    @Modifying(clearAutomatically = true)
    @Query("update RefreshToken r set r.revokedAt = :now where r.user.id = :userId and r.revokedAt is null")
    int revokeAllActiveByUserId(Long userId, LocalDateTime now);
}
