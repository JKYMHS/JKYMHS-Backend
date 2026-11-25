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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RefreshToken r where r.tokenHash = :tokenHash")
    Optional<RefreshToken> findByTokenHashForUpdate(String tokenHash);

    @Modifying(clearAutomatically = true)
    @Query("""
            update RefreshToken r
            set r.revokedAt = coalesce(r.revokedAt, :now), r.rotatedAt = null
            where r.user.id = :userId and (r.revokedAt is null or r.rotatedAt is not null)
            """)
    int revokeAllByUserId(Long userId, LocalDateTime now);

    @Modifying(clearAutomatically = true)
    @Query("""
            update RefreshToken r
            set r.revokedAt = coalesce(r.revokedAt, :now), r.rotatedAt = null
            where r.user.id = :userId and r.familyId = :familyId and (r.revokedAt is null or r.rotatedAt is not null)
            """)
    int revokeFamily(Long userId, String familyId, LocalDateTime now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from RefreshToken r where r.user.id = :userId")
    int deleteAllByUserId(Long userId);
}
