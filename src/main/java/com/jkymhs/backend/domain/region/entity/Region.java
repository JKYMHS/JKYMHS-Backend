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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Region parent;

    @Column(name = "ldong_regn_cd", nullable = false, length = 5)
    private String ldongRegnCd;

    @Column(name = "ldong_signgu_cd", nullable = false, length = 3)
    private String ldongSignguCd;

    @Column(nullable = false, length = 50)
    private String name;

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
