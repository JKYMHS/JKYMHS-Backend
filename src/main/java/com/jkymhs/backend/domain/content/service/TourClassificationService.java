package com.jkymhs.backend.domain.content.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TourClassificationService {

    private static final String UPSERT_SQL = """
            INSERT INTO tour_classifications (code, depth, parent_code, name, io_type) VALUES (?, ?, ?, ?, ?) AS new
            ON DUPLICATE KEY UPDATE depth = new.depth, parent_code = new.parent_code, name = new.name, io_type = new.io_type
            """;

    private final JdbcTemplate jdbcTemplate;

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
