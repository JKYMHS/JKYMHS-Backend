package com.jkymhs.backend.domain.content.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public final class ContentRedisKeys {

    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.BASIC_ISO_DATE;

    private ContentRedisKeys() {
    }

    public static String popularityVersion() {
        return "popularity:version";
    }

    public static String candidatesPattern() {
        return "candidates:*";
    }

    public static String tourApiCalls(LocalDate date) {
        return "tourapi:calls:" + date.format(YYYYMMDD);
    }

    public static String detailLock(Long contentId) {
        return "lock:detail:" + contentId;
    }
}
