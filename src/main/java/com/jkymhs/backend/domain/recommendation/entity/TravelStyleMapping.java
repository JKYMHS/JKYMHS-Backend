package com.jkymhs.backend.domain.recommendation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 여행 스타일(rest, culture…) → TourAPI 분류 코드(lclsSystm). 코드 길이로 단계를 구분한다:
 * 2자(NA)는 대분류, 4자(EX05)는 중분류. 콘텐츠의 lcls1/lcls2/lcls3 중 하나가 이 코드로 시작하면 그 스타일에 맞는 곳이다.
 * 운영 중에 가중치를 바꿔 볼 수 있게 enum 대신 테이블로 두고 {@code TravelStyleMappingSeeder}가 채운다.
 */
@Getter
@Entity
@Table(name = "travel_style_mappings", uniqueConstraints = @UniqueConstraint(
        name = "uk_travel_style_mappings_style_code_lcls_code", columnNames = {"style_code", "lcls_code"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TravelStyleMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // TravelStyle.name() (REST, CULTURE…)
    @Column(name = "style_code", nullable = false, length = 20)
    private String styleCode;

    @Column(name = "lcls_code", nullable = false, length = 10)
    private String lclsCode;

    private TravelStyleMapping(String styleCode, String lclsCode) {
        this.styleCode = styleCode;
        this.lclsCode = lclsCode;
    }

    public static TravelStyleMapping of(String styleCode, String lclsCode) {
        return new TravelStyleMapping(styleCode, lclsCode);
    }
}
