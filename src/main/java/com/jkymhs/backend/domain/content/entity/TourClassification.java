package com.jkymhs.backend.domain.content.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 관광공사 신 분류체계(lclsSystm). 대분류 "VE"(2자) → 중분류 "VE07"(4자) → 소분류 "VE070100"(8자) 세 단계다.
 * 옛 분류(cat1/cat2/cat3)는 KorService2에서 더 이상 갱신되지 않아서 신 분류를 쓴다.
 *
 * 코드 자체가 PK다. 관광공사가 정한 값이라 바뀌지 않고, 콘텐츠도 코드 문자열(lcls1/2/3)로 들고 있다.
 * 행은 CSV 적재(TourCsvImporter)가 JdbcTemplate로 넣는다.
 */
@Getter
@Entity
@Table(name = "tour_classifications")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TourClassification {

    @Id
    @Column(length = 10)
    private String code;

    // 1 대분류, 2 중분류, 3 소분류
    @Column(nullable = false, columnDefinition = "tinyint")
    private int depth;

    // 대분류이면 null
    @Column(name = "parent_code", length = 10)
    private String parentCode;

    @Column(nullable = false, length = 50)
    private String name;

    // 소분류에만 있다 (indoor_outdoor.csv)
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "io_type", length = 10)
    private IoType ioType;
}
