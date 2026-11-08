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

/**
 * tools/tourapi/collect.py가 만든 CSV를 DB에 넣는다. {@code tour-api.import.csv-dir}(TOUR_IMPORT_CSV_DIR)가 있을 때만 서버 시작 시 돈다.
 *
 * 처음 데이터를 API로 전부 받으면 수천 번 호출이 필요해서 개발 계정 하루 한도(1000회)로는 며칠이 걸린다.
 * 그래서 수집기로 한 번 받아둔 CSV를 넣고, 이후 변경분만 매일 동기화 배치({@link TourSyncJob})가 받는다.
 *
 * 순서: 지역(regions.csv) → 분류(lcls_codes.csv + classpath:seed/indoor_outdoor.csv) → 카테고리 시드 →
 * 콘텐츠(contents.csv, 지역 id·카테고리·실내외를 이때 정한다) → 운영 정보(content_details.csv → 운영 시간·쉬는 날·축제 시간 컬럼).
 * 모두 upsert라서 몇 번을 돌려도 결과가 같다. 끝나면 동기화 기준일을 CSV의 가장 최근 수정일로 맞춘다.
 *
 * 실패해도 서버 시작을 막지 않는다 (FAILED 기록 + 알림). 탐색 API는 빈 결과로라도 뜨는 편이 낫다.
 */
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

    // unreadable: id·종류를 읽지 못해 ContentSource를 만들 수 없었던 줄 수
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

    /** @return {처리한 줄, contents에 없어서 건너뛴 줄} */
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
            // collect.py는 운영 시간과 축제 공연 시간을 operating_hours 한 칸에 넣는다. 축제인지는 applyIntro가 종류를 보고 나눈다
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

    // collect.py는 엑셀에서 한글이 깨지지 않게 UTF-8 BOM(utf-8-sig)으로 쓴다. BOM이 남으면 첫 헤더 이름이 "﻿content_id"가 된다
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
                // 아래에서 contentTypeId로 정한다
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
