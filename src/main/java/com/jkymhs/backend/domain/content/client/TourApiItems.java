package com.jkymhs.backend.domain.content.client;

import com.jkymhs.backend.domain.content.entity.ContentType;
import com.jkymhs.backend.domain.content.service.ContentSource;
import com.jkymhs.backend.domain.content.service.TourIntro;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Map;

/**
 * TourAPI 응답 항목(필드 이름 → 문자열) → 적재용 record. 형식이 틀린 값은 null로 둔다 (한 건 때문에 배치 전체가 멈추지 않게).
 */
public final class TourApiItems {

    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter YYYYMMDDHHMMSS = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private TourApiItems() {
    }

    /**
     * 목록 항목(areaBasedList2, areaBasedSyncList2, searchFestival2) → 콘텐츠.
     *
     * @return 다루지 않는 종류(레포츠·쇼핑 등)이거나 id가 없으면 null
     */
    public static ContentSource toContentSource(Map<String, String> item) {
        Long id = parseLong(item.get("contentid"));
        Integer typeId = parseInt(item.get("contenttypeid"));
        ContentType type = typeId == null ? null : ContentType.ofTourTypeId(typeId);
        if (id == null || type == null) {
            return null;
        }
        String address = join(item.get("addr1"), item.get("addr2"));
        return new ContentSource(id, type, typeId, text(item.get("title")), address,
                coordinate(item.get("mapy")), coordinate(item.get("mapx")),
                text(item.get("lDongRegnCd")), text(item.get("lDongSignguCd")),
                text(item.get("lclsSystm1")), text(item.get("lclsSystm2")), text(item.get("lclsSystm3")),
                text(item.get("firstimage")), text(item.get("tel")),
                date(item.get("eventstartdate")), date(item.get("eventenddate")),
                dateTime(item.get("modifiedtime")));
    }

    /** showflag=0이면 TourAPI에서 내려간 콘텐츠 */
    public static boolean isHidden(Map<String, String> item) {
        return "0".equals(text(item.get("showflag")));
    }

    public static Long contentId(Map<String, String> item) {
        return parseLong(item.get("contentid"));
    }

    /** detailIntro2 → 운영 정보. 관광지·문화시설·축제가 쓰는 필드 이름이 달라서 있는 쪽을 고른다 */
    public static TourIntro toIntro(Map<String, String> item) {
        if (item == null) {
            return null;
        }
        return new TourIntro(
                first(item.get("usetime"), item.get("usetimeculture")),
                first(item.get("restdate"), item.get("restdateculture")),
                text(item.get("playtime")),
                text(item.get("eventstarttime")),
                text(item.get("eventplace")),
                first(item.get("usefee"), item.get("usetimefestival")),
                text(item.get("agelimit")),
                text(item.get("sponsor1")),
                first(item.get("infocenter"), item.get("sponsor1tel"), item.get("infocenterculture")),
                text(item.get("program")));
    }

    /**
     * detailPetTour2의 동반 구분(acmpyTypeCd: "동반가능", "일부구역 동반가능", "동반불가") → 동반 가능 여부.
     *
     * @return 정보가 없으면 null (모름)
     */
    public static Boolean petAllowed(Map<String, String> item) {
        if (item == null) {
            return null;
        }
        String type = text(item.get("acmpyTypeCd"));
        if (type == null) {
            return null;
        }
        if (type.contains("불가")) {
            return false;
        }
        return type.contains("가능") ? true : null;
    }

    static String text(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String first(String... values) {
        for (String value : values) {
            String text = text(value);
            if (text != null) {
                return text;
            }
        }
        return null;
    }

    private static String join(String first, String second) {
        String a = text(first);
        String b = text(second);
        if (a == null) {
            return b;
        }
        return b == null ? a : a + " " + b;
    }

    public static BigDecimal coordinate(String value) {
        String text = text(value);
        if (text == null) {
            return null;
        }
        try {
            return new BigDecimal(text).setScale(7, RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static LocalDate date(String value) {
        String text = text(value);
        if (text == null || text.length() < 8) {
            return null;
        }
        try {
            return LocalDate.parse(text.substring(0, 8), YYYYMMDD);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static LocalDateTime dateTime(String value) {
        String text = text(value);
        if (text == null || text.length() < 14) {
            return null;
        }
        try {
            return LocalDateTime.parse(text.substring(0, 14), YYYYMMDDHHMMSS);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static Long parseLong(String value) {
        String text = text(value);
        try {
            return text == null ? null : Long.parseLong(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer parseInt(String value) {
        String text = text(value);
        try {
            return text == null ? null : Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
