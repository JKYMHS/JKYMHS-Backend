package com.jkymhs.backend.domain.content.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 관광공사 분류 코드(tour_classifications) 적재.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TourClassificationService {

    private static final String UPSERT_SQL = """
            INSERT INTO tour_classifications (code, depth, parent_code, name, io_type) VALUES (?, ?, ?, ?, ?) AS new
            ON DUPLICATE KEY UPDATE depth = new.depth, parent_code = new.parent_code, name = new.name, io_type = new.io_type
            """;

    private final JdbcTemplate jdbcTemplate;

    /**
     * 코드 기준으로 넣거나 이름·실내외를 고친다.
     *
     * @return 넣거나 고친 행 수
     */
    @Transactional
    public int upsertAll(List<ClassificationSource> sources) {
        List<Object[]> rows = sources.stream()
                .map(s -> new Object[]{s.code(), s.depth(), s.parentCode(), s.name(),
                        s.ioType() == null ? null : s.ioType().name()})
                .toList();
        jdbcTemplate.batchUpdate(UPSERT_SQL, rows);
        return rows.size();
    }
}
