package com.jkymhs.backend.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 테스트에서 "지금"을 정하고 옮길 수 있는 시계. 기본값은 2026-11-09(월) 10:00 KST.
 * 백오프·만료처럼 시간이 지나야 하는 동작은 sleep 대신 {@link #advance}로 시계를 옮겨서 확인한다.
 */
public class MutableClock extends Clock {

    public static final ZoneId KST = ZoneId.of("Asia/Seoul");
    public static final LocalDateTime DEFAULT_NOW = LocalDateTime.of(2026, 11, 9, 10, 0);

    private volatile Instant instant;

    public MutableClock() {
        reset();
    }

    public void setNow(LocalDateTime kstDateTime) {
        this.instant = kstDateTime.atZone(KST).toInstant();
    }

    public void setInstant(Instant instant) {
        this.instant = instant;
    }

    public void advance(Duration duration) {
        this.instant = instant.plus(duration);
    }

    public void reset() {
        setNow(DEFAULT_NOW);
    }

    @Override
    public ZoneId getZone() {
        return KST;
    }

    // 다른 시간대가 필요하면 그 순간을 고정한 시계를 준다 (이후 advance는 반영되지 않는다)
    @Override
    public Clock withZone(ZoneId zone) {
        return KST.equals(zone) ? this : Clock.fixed(instant, zone);
    }

    @Override
    public Instant instant() {
        return instant;
    }
}
