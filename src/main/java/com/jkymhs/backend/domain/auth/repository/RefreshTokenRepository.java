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

    /*
     * 재발급·로그아웃용 조회. SELECT ... FOR UPDATE로 행을 잠가서 트랜잭션이 끝날 때까지 같은 행을 읽으려는 다른 요청을 기다리게 한다.
     * 잠그지 않으면 같은 토큰으로 재발급 요청이 동시에 두 번 왔을 때 둘 다 "아직 폐기 안 됨"을 보고 같은 행을 고친다.
     * 잠그면 두 번째 요청은 첫 번째가 교체한 뒤의 상태(rotatedAt)를 읽어서 TokenService의 유예 시간 규칙을 탄다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RefreshToken r where r.tokenHash = :tokenHash")
    Optional<RefreshToken> findByTokenHashForUpdate(String tokenHash);

    /*
     * 그 회원의 모든 토큰을 UPDATE 한 번으로 폐기한다 (탈취 의심 시 모든 기기 로그아웃).
     * coalesce: 이미 폐기된 토큰은 원래 폐기 시각을 유지한다.
     * rotatedAt = null: 방금 교체된 토큰이 있어도 지운다. 남겨 두면 탈취가 확인된 뒤에도 그 토큰이 유예 시간 규칙으로 새 세션을 받아가고,
     * 유예 시간이 지난 뒤에는 그 토큰을 다시 보낼 때마다 일괄 폐기가 또 일어나서 공격자가 사용자를 계속 로그아웃시킬 수 있다.
     * clearAutomatically: 이 UPDATE는 영속성 컨텍스트(1차 캐시)를 거치지 않고 DB에 바로 실행된다.
     * 캐시를 비워야 이후에 엔티티를 다시 읽을 때 바뀌기 전 값을 보지 않는다.
     */
    @Modifying(clearAutomatically = true)
    @Query("""
            update RefreshToken r
            set r.revokedAt = coalesce(r.revokedAt, :now), r.rotatedAt = null
            where r.user.id = :userId and (r.revokedAt is null or r.rotatedAt is not null)
            """)
    int revokeAllByUserId(Long userId, LocalDateTime now);

    /*
     * 한 로그인(family)의 토큰만 폐기한다 (로그아웃). 다른 기기의 로그인은 그대로 둔다.
     * rotatedAt을 비우는 이유는 위와 같다. 비우지 않으면 로그아웃한 뒤에도 방금 교체된 토큰이 유예 시간 규칙으로 새 토큰을 받아간다.
     */
    @Modifying(clearAutomatically = true)
    @Query("""
            update RefreshToken r
            set r.revokedAt = coalesce(r.revokedAt, :now), r.rotatedAt = null
            where r.user.id = :userId and r.familyId = :familyId and (r.revokedAt is null or r.rotatedAt is not null)
            """)
    int revokeFamily(Long userId, String familyId, LocalDateTime now);

    /*
     * 그 회원의 리프레시 토큰을 모두 지운다 (비밀번호 변경·탈퇴).
     * 폐기(UPDATE) 대신 지우는 이유: 탈퇴는 users 행을 지우기 전에 FK가 걸린 토큰 행을 먼저 없애야 하고,
     * 비밀번호를 바꾼 뒤에는 옛 토큰이 다시 와도 "없는 토큰 → 401"이면 충분해서 폐기 기록을 남길 필요가 없다.
     * flushAutomatically: 이 DELETE 전에 같은 트랜잭션에서 바꾼 엔티티(새 비밀번호 해시 등)를 먼저 DB에 쓴다.
     * clearAutomatically가 1차 캐시를 비우므로, 먼저 쓰지 않으면 그 변경이 버려진다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from RefreshToken r where r.user.id = :userId")
    int deleteAllByUserId(Long userId);
}
