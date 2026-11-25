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
