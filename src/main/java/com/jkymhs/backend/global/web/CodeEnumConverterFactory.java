package com.jkymhs.backend.global.web;

import org.springframework.core.convert.converter.Converter;
import org.springframework.core.convert.converter.ConverterFactory;

/**
 * 쿼리 파라미터 문자열을 {@link CodeEnum} 상수로 바꾼다. 예: {@code ?companion=kids} → {@code Companion.KIDS}.
 *
 * 스프링 기본 변환기는 name()과 정확히 같아야만 바꿔서 {@code "kids"}를 받지 못한다.
 * 이 팩토리는 대상 타입이 CodeEnum일 때만 쓰인다 (스프링은 enum 자신 → 구현 인터페이스 → Enum 순으로 변환기를 찾는다).
 *
 * 찾는 순서: code()와 정확히 같은 값 → name()과 대소문자 무시로 같은 값.
 * name()도 받아주는 이유: 대문자 enum(예: ChildAgeGroup)은 code와 name이 같고, 운영 중 수동 호출에서 대문자를 써도 동작하게 하려고.
 * 둘 다 없으면 IllegalArgumentException → 스프링이 MethodArgumentTypeMismatchException(@RequestParam)이나
 * 바인딩 오류(@ModelAttribute)로 바꿔서 400 INVALID_INPUT이 된다.
 */
public class CodeEnumConverterFactory implements ConverterFactory<String, CodeEnum> {

    @Override
    public <T extends CodeEnum> Converter<String, T> getConverter(Class<T> targetType) {
        return source -> convert(targetType, source);
    }

    private static <T extends CodeEnum> T convert(Class<T> targetType, String source) {
        String value = source.trim();
        // 스프링 기본 enum 변환기와 같이 빈 문자열은 "값 없음"(null)으로 본다. 필수 여부는 @NotNull이 판단한다
        if (value.isEmpty()) {
            return null;
        }
        T[] constants = targetType.getEnumConstants();
        if (constants == null) {
            throw new IllegalArgumentException(targetType.getSimpleName() + "는 enum이 아닙니다.");
        }
        for (T constant : constants) {
            if (constant.code().equals(value)) {
                return constant;
            }
        }
        for (T constant : constants) {
            if (((Enum<?>) constant).name().equalsIgnoreCase(value)) {
                return constant;
            }
        }
        throw new IllegalArgumentException(
                "지원하지 않는 " + targetType.getSimpleName() + " 값: " + value);
    }
}
