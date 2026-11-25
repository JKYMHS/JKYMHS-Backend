package com.jkymhs.backend.domain.category.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Entity
@Table(name = "categories", uniqueConstraints = @UniqueConstraint(name = "uk_categories_code", columnNames = "code"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30)
    private String code;

    @Column(nullable = false, length = 50)
    private String name;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "group_type", nullable = false, length = 20)
    private CategoryGroupType groupType;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    private Category(String code, String name, CategoryGroupType groupType, int sortOrder) {
        this.code = code;
        this.name = name;
        this.groupType = groupType;
        this.sortOrder = sortOrder;
    }

    public static Category create(String code, String name, CategoryGroupType groupType, int sortOrder) {
        return new Category(code, name, groupType, sortOrder);
    }

    public void update(String name, CategoryGroupType groupType, int sortOrder) {
        this.name = name;
        this.groupType = groupType;
        this.sortOrder = sortOrder;
    }
}
