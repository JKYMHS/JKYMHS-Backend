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

@Getter
@Entity
@Table(name = "tour_classifications")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TourClassification {

    @Id
    @Column(length = 10)
    private String code;

    @Column(nullable = false, columnDefinition = "tinyint")
    private int depth;

    @Column(name = "parent_code", length = 10)
    private String parentCode;

    @Column(nullable = false, length = 50)
    private String name;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "io_type", length = 10)
    private IoType ioType;
}
