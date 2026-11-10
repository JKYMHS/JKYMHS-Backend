package com.jkymhs.backend.domain.region.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 법정동 기준 지역. 시·도(depth 1)와 그 아래 시·군·구(depth 2) 두 단계만 둔다.
 *
 * 코드 체계를 옛 관광공사 지역코드(areaCode) 대신 법정동 코드(lDongRegnCd/lDongSignguCd)로 쓰는 이유:
 * KorService2 목록 응답이 법정동 코드를 주고, 2026년 행정구역 개편(전남·광주 통합 등)도 법정동 코드에 먼저 반영된다.
 *
 * 행은 CSV 적재(TourCsvImporter)가 JdbcTemplate로 넣는다. 코드가 같으면 id를 유지한 채 이름만 바꾸므로
 * 다른 테이블이 들고 있는 region_id가 재적재 후에도 그대로 맞는다. 그래서 이 엔티티에는 상태를 바꾸는 메서드가 없다.
 */
@Getter
@Entity
@Table(name = "regions", uniqueConstraints = @UniqueConstraint(
        name = "uk_regions_ldong_regn_cd_ldong_signgu_cd", columnNames = {"ldong_regn_cd", "ldong_signgu_cd"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Region {

    public static final int DEPTH_SIDO = 1;
    public static final int DEPTH_SIGUNGU = 2;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // null이면 시·도
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Region parent;

    // 시·도 법정동 코드. 세종은 시·군·구가 없어서 관광공사가 주는 5자리 36110을 그대로 쓴다
    @Column(name = "ldong_regn_cd", nullable = false, length = 5)
    private String ldongRegnCd;

    // 시·도 행은 ''(빈 문자열). NULL로 두면 MySQL UNIQUE가 NULL끼리는 중복으로 보지 않아서 같은 시·도가 두 번 들어갈 수 있다
    @Column(name = "ldong_signgu_cd", nullable = false, length = 3)
    private String ldongSignguCd;

    // 공식 이름 ("서울특별시", "중구")
    @Column(nullable = false, length = 50)
    private String name;

    // 화면에 보여줄 이름 ("서울", "중구"). 모든 API의 지역 이름은 이 값이다
    @Column(name = "short_name", nullable = false, length = 20)
    private String shortName;

    @Column(nullable = false, columnDefinition = "tinyint")
    private int depth;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    public boolean isSido() {
        return depth == DEPTH_SIDO;
    }
}
