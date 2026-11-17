package com.jkymhs.backend.domain.content.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 상세 화면에만 쓰는 긴 값 (소개글, 요금 등). 목록 조회가 contents만 읽도록 따로 둔다 (overview는 수 KB짜리 TEXT).
 * PK가 contents.id와 같은 1:1 테이블이다 ({@code @MapsId}).
 *
 * 행은 CSV 적재·동기화(detailIntro2 → 요금·문의처 등)나 상세 갱신(detailCommon2 → 소개글)이 JdbcTemplate로 넣는다.
 * 그래서 소개글(overview)이 아직 없는 행도 있다. {@code fetchedAt}은 상세(detailCommon2)를 마지막으로 받은 시각이고,
 * 운영 정보로만 만들어진 행은 null이다. 상세 화면(D1)은 null이거나 오래됐으면 TourAPI에서 다시 받는다
 * (행을 만든 시각을 넣으면 소개글 없이 "방금 받은 상세"로 보여서 7일 동안 다시 받지 않게 된다).
 */
@Getter
@Entity
@Table(name = "content_details")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContentDetail {

    @Id
    @Column(name = "content_id")
    private Long contentId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "content_id")
    private Content content;

    @Column(columnDefinition = "text")
    private String overview;

    @Column(length = 500)
    private String homepage;

    @Column(length = 500)
    private String fee;

    @Column(length = 100)
    private String contact;

    private String host;

    @Column(name = "age_limit", length = 100)
    private String ageLimit;

    @Column(name = "event_program", columnDefinition = "text")
    private String eventProgram;

    @Column(name = "fetched_at")
    private LocalDateTime fetchedAt;
}
