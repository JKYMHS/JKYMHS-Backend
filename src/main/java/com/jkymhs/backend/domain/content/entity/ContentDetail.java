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
