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

/**
 * 서비스 카테고리 (탐색 필터·홈 칩). 관광공사 분류(tour_classifications)는 250개 가까이 되고 이름도 FE 화면과 맞지 않아서,
 * 화면에 보여줄 묶음을 따로 두고 {@link CategoryMapping}으로 분류 코드와 잇는다.
 * 행은 {@code CategorySeeder}가 코드(code) 기준으로 넣고 고친다. FE·다른 도메인은 id 대신 code를 기준으로 삼아도 된다.
 */
@Getter
@Entity
@Table(name = "categories", uniqueConstraints = @UniqueConstraint(name = "uk_categories_code", columnNames = "code"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // FE와 주고받는 값 ("history", "with_kids")
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

    /** 시드 값이 바뀌었을 때 같은 행(id 유지)을 고친다. */
    public void update(String name, CategoryGroupType groupType, int sortOrder) {
        this.name = name;
        this.groupType = groupType;
        this.sortOrder = sortOrder;
    }
}
