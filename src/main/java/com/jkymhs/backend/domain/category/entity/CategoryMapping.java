package com.jkymhs.backend.domain.category.entity;

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
 * 카테고리 ↔ 관광공사 분류 코드. 코드는 어느 단계든 될 수 있다 ("HS"는 대분류 전체, "VE07"은 중분류, "HS010100"은 소분류 하나).
 * 콘텐츠의 대·중·소분류(lcls1/2/3) 중 하나라도 이 코드와 같으면 그 카테고리에 속한다.
 */
@Getter
@Entity
@Table(name = "category_mappings", uniqueConstraints = @UniqueConstraint(
        name = "uk_category_mappings_category_id_lcls_code", columnNames = {"category_id", "lcls_code"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CategoryMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(name = "lcls_code", nullable = false, length = 10)
    private String lclsCode;

    private CategoryMapping(Category category, String lclsCode) {
        this.category = category;
        this.lclsCode = lclsCode;
    }

    public static CategoryMapping create(Category category, String lclsCode) {
        return new CategoryMapping(category, lclsCode);
    }
}
