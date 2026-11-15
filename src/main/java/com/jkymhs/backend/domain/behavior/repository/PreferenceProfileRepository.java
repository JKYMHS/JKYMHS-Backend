package com.jkymhs.backend.domain.behavior.repository;

import com.jkymhs.backend.domain.behavior.entity.PreferenceProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface PreferenceProfileRepository extends JpaRepository<PreferenceProfile, Long> {

    List<PreferenceProfile> findAllByUserIdAndProfileKey(Long userId, String profileKey);

    /**
     * 같은 (회원, 키, 분류) 행이 있으면 값만 바꾼다. 같은 회원의 행동 두 개가 동시에 처리돼도 유니크 키 충돌이 나지 않는다.
     * {@code AS new}는 MySQL 8.0.19+의 문법이다 (예전 {@code VALUES(col)} 함수는 8.0.20부터 deprecated).
     */
    @Modifying
    @Query(value = "INSERT INTO preference_profiles (user_id, profile_key, lcls2, score, signal_count, updated_at) "
            + "VALUES (:userId, :profileKey, :lcls2, :score, :signalCount, :now) AS new "
            + "ON DUPLICATE KEY UPDATE score = new.score, signal_count = new.signal_count, updated_at = new.updated_at",
            nativeQuery = true)
    int upsert(@Param("userId") Long userId, @Param("profileKey") String profileKey, @Param("lcls2") String lcls2,
               @Param("score") BigDecimal score, @Param("signalCount") int signalCount, @Param("now") LocalDateTime now);

    // 이번 계산에 없는 분류(180일이 지나 기록이 빠진 분류)를 지운다
    @Modifying
    @Query("delete from PreferenceProfile p where p.userId = :userId and p.profileKey = :profileKey "
            + "and p.lcls2 not in :keep")
    int deleteOthers(@Param("userId") Long userId, @Param("profileKey") String profileKey,
                     @Param("keep") Collection<String> keep);

    @Modifying
    @Query("delete from PreferenceProfile p where p.userId = :userId and p.profileKey = :profileKey")
    int deleteAllByUserIdAndProfileKey(@Param("userId") Long userId, @Param("profileKey") String profileKey);

    @Modifying
    @Query("delete from PreferenceProfile p where p.userId = :userId")
    int deleteAllByUserId(@Param("userId") Long userId);
}
