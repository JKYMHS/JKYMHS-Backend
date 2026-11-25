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

@Getter
@Entity
@Table(name = "travel_style_mappings", uniqueConstraints = @UniqueConstraint(
        name = "uk_travel_style_mappings_style_code_lcls_code", columnNames = {"style_code", "lcls_code"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TravelStyleMapping {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

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
