package com.jkymhs.backend.domain.content.batch;

import com.jkymhs.backend.domain.category.service.CategorySeeder;
import com.jkymhs.backend.domain.content.client.TourApiItems;
import com.jkymhs.backend.domain.content.entity.BatchJobLog;
import com.jkymhs.backend.domain.content.entity.BatchJobName;
import com.jkymhs.backend.domain.content.entity.BatchJobStatus;
import com.jkymhs.backend.domain.content.entity.ContentType;
import com.jkymhs.backend.domain.content.entity.IoType;
import com.jkymhs.backend.domain.content.entity.TourSyncCursorName;
import com.jkymhs.backend.domain.content.service.BatchJobLogService;
import com.jkymhs.backend.domain.content.service.ClassificationSource;
import com.jkymhs.backend.domain.content.service.ContentLoadContext;
import com.jkymhs.backend.domain.content.service.ContentService;
import com.jkymhs.backend.domain.content.service.ContentSource;
import com.jkymhs.backend.domain.content.service.TourClassificationService;
import com.jkymhs.backend.domain.content.service.TourIntro;
import com.jkymhs.backend.domain.content.service.TourSyncCursorService;
import com.jkymhs.backend.domain.region.service.RegionService;
import com.jkymhs.backend.domain.region.service.RegionSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Component
@Order(2)
@RequiredArgsConstructor
public class TourCsvImporter implements ApplicationRunner {

    static final String INDOOR_OUTDOOR_SEED = "seed/indoor_outdoor.csv";
    private static final DateTimeFormatter MODIFIED_AT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int CONTENT_CHUNK = 1000;

    private final TourImportProperties importProperties;
    private final RegionService regionService;
    private final TourClassificationService tourClassificationService;
    private final CategorySeeder categorySeeder;
    private final ContentService contentService;
    private final TourSyncCursorService cursorService;
    private final BatchJobLogService batchJobLogService;
    private final BatchAlertNotifier alertNotifier;

    @Override
    public void run(ApplicationArguments args) {
        if (!importProperties.enabled()) {
            return;
        }
        Path dir = Path.of(importProperties.csvDir());
        try {
            importFrom(dir);
        } catch (RuntimeException e) {
            log.error("TourAPI CSV import failed: {}", dir, e);
        }
    }

    public TourImportResult importFrom(Path dir) {
        BatchJobLog jobLog = batchJobLogService.start(BatchJobName.TOUR_IMPORT);
        try {
            int regions = regionService.upsertAll(readRegions(dir.resolve("regions.csv")));
            int classifications = tourClassificationService.upsertAll(readClassifications(dir.resolve("lcls_codes.csv")));
            categorySeeder.seed();

            ContentLoadContext context = contentService.loadContext();
            ParsedContents parsed = readContents(dir.resolve("contents.csv"));
            List<ContentSource> sources = parsed.sources();
            int contents = 0;
            for (int i = 0; i < sources.size(); i += CONTENT_CHUNK) {
                contents += contentService.upsert(sources.subList(i, Math.min(i + CONTENT_CHUNK, sources.size())), context);
            }
            int skippedContents = sources.size() - contents + parsed.unreadable();

            int[] detailCounts = importDetails(dir.resolve("content_details.csv"));
            contentService.findMaxTourModifiedAt().ifPresent(max ->
                    cursorService.set(TourSyncCursorName.MODIFIED_SINCE, max.toLocalDate().toString()));

            TourImportResult result = new TourImportResult(regions, classifications, contents, skippedContents,
                    detailCounts[0], detailCounts[1]);
            batchJobLogService.finish(jobLog, BatchJobStatus.SUCCESS, contents + detailCounts[0],
                    0, 0, null);
            log.info("TourAPI CSV import done: {}", result);
            return result;
        } catch (RuntimeException e) {
            batchJobLogService.finish(jobLog, BatchJobStatus.FAILED, 0, 0, 0,
                    e.getClass().getSimpleName() + ": " + e.getMessage());
            alertNotifier.notifyFailure("[TOUR_IMPORT] CSV 적재 실패: " + e.getMessage());
            throw e;
        }
    }

    private List<RegionSource> readRegions(Path file) {
        List<RegionSource> sources = new ArrayList<>();
        for (CSVRecord record : readCsv(file)) {
            String code = value(record, "area_code");
            String name = value(record, "name");
            if (code != null && name != null) {
                sources.add(new RegionSource(code, value(record, "sigungu_code"), name));
            }
        }
        return sources;
    }

    private List<ClassificationSource> readClassifications(Path file) {
        Map<String, IoType> ioTypes = readIndoorOutdoor();
        Map<String, ClassificationSource> byCode = new LinkedHashMap<>();
        for (CSVRecord record : readCsv(file)) {
            String code1 = value(record, "lcls1_cd");
            String code2 = value(record, "lcls2_cd");
            String code3 = value(record, "lcls3_cd");
            if (code1 != null) {
                byCode.putIfAbsent(code1, new ClassificationSource(code1, 1, null, value(record, "lcls1_nm"), null));
            }
            if (code2 != null) {
                byCode.putIfAbsent(code2, new ClassificationSource(code2, 2, code1, value(record, "lcls2_nm"), null));
            }
            if (code3 != null) {
                byCode.putIfAbsent(code3, new ClassificationSource(code3, 3, code2, value(record, "lcls3_nm"),
                        ioTypes.get(code3)));
            }
        }
        return byCode.values().stream().filter(s -> s.name() != null).toList();
    }

    private Map<String, IoType> readIndoorOutdoor() {
        Map<String, IoType> ioTypes = new HashMap<>();
        ClassPathResource resource = new ClassPathResource(INDOOR_OUTDOOR_SEED);
        try (InputStream in = resource.getInputStream();
             Reader reader = skipBom(new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)));
             CSVParser parser = csvFormat().parse(reader)) {
            for (CSVRecord record : parser) {
                String code = value(record, "lcls3_cd");
                IoType io = IoType.parse(value(record, "io"));
                if (code != null && io != null) {
                    ioTypes.put(code, io);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("실내외 시드 파일을 읽을 수 없음: " + INDOOR_OUTDOOR_SEED, e);
        }
        return ioTypes;
    }

    private ParsedContents readContents(Path file) {
        List<ContentSource> sources = new ArrayList<>();
        int unreadable = 0;
        for (CSVRecord record : readCsv(file)) {
            ContentSource source = toContentSource(record);
            if (source == null) {
                unreadable++;
            } else {
                sources.add(source);
            }
        }
        return new ParsedContents(sources, unreadable);
    }

    private record ParsedContents(List<ContentSource> sources, int unreadable) {
    }

    private static ContentSource toContentSource(CSVRecord record) {
        Long id = parseLong(value(record, "content_id"));
        Integer tourTypeId = parseInt(value(record, "tour_content_type_id"));
        if (id == null || tourTypeId == null) {
            return null;
        }
        ContentType type = parseType(value(record, "content_type"), tourTypeId);
        if (type == null) {
            return null;
        }
        return new ContentSource(id, type, tourTypeId, value(record, "name"), value(record, "address"),
                coordinate(value(record, "latitude")), coordinate(value(record, "longitude")),
                value(record, "ldong_regn_cd"), value(record, "ldong_signgu_cd"),
                value(record, "lcls1"), value(record, "lcls2"), value(record, "tour_cat_code"),
                value(record, "thumbnail_url"), value(record, "tel"),
                parseDate(value(record, "event_start_date")), parseDate(value(record, "event_end_date")),
                parseDateTime(value(record, "tour_modified_at")));
    }

    private int[] importDetails(Path file) {
        if (!Files.exists(file)) {
            log.warn("content_details.csv not found in {}, skipping details", file.getParent());
            return new int[]{0, 0};
        }
        List<CSVRecord> records = readCsv(file);
        List<Long> ids = records.stream().map(r -> parseLong(value(r, "content_id"))).filter(Objects::nonNull).toList();
        Map<Long, ContentType> types = contentService.findTypes(ids);
        int applied = 0;
        int skipped = 0;
        for (CSVRecord record : records) {
            Long id = parseLong(value(record, "content_id"));
            if (id == null || !types.containsKey(id)) {
                skipped++;
                continue;
            }
            TourIntro intro = new TourIntro(value(record, "operating_hours"), value(record, "rest_date"), null,
                    value(record, "event_start_time"), value(record, "event_place"), value(record, "fee"),
                    value(record, "age_limit"), value(record, "host"), value(record, "contact"), null);
            if (contentService.applyIntro(id, intro)) {
                applied++;
            } else {
                skipped++;
            }
        }
        return new int[]{applied, skipped};
    }

    private static List<CSVRecord> readCsv(Path file) {
        try (Reader reader = skipBom(Files.newBufferedReader(file, StandardCharsets.UTF_8));
             CSVParser parser = csvFormat().parse(reader)) {
            return parser.getRecords();
        } catch (IOException e) {
            throw new UncheckedIOException("CSV를 읽을 수 없음: " + file, e);
        }
    }

    private static CSVFormat csvFormat() {
        return CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreEmptyLines(true)
                .setTrim(true)
                .get();
    }

    private static Reader skipBom(BufferedReader reader) throws IOException {
        reader.mark(1);
        if (reader.read() != '﻿') {
            reader.reset();
        }
        return reader;
    }

    private static String value(CSVRecord record, String column) {
        if (!record.isMapped(column) || !record.isSet(column)) {
            return null;
        }
        String value = record.get(column);
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static ContentType parseType(String value, int tourTypeId) {
        if (value != null) {
            try {
                return ContentType.valueOf(value);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return ContentType.ofTourTypeId(tourTypeId);
    }

    private static BigDecimal coordinate(String value) {
        return TourApiItems.coordinate(value);
    }

    private static Long parseLong(String value) {
        try {
            return value == null ? null : Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer parseInt(String value) {
        try {
            return value == null ? null : Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static LocalDate parseDate(String value) {
        try {
            return value == null ? null : LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static LocalDateTime parseDateTime(String value) {
        try {
            return value == null ? null : LocalDateTime.parse(value, MODIFIED_AT);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
