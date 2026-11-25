package com.jkymhs.backend.global.web;

import org.springframework.core.convert.converter.Converter;
import org.springframework.core.convert.converter.ConverterFactory;

public class CodeEnumConverterFactory implements ConverterFactory<String, CodeEnum> {

    @Override
    public <T extends CodeEnum> Converter<String, T> getConverter(Class<T> targetType) {
        return source -> convert(targetType, source);
    }

    private static <T extends CodeEnum> T convert(Class<T> targetType, String source) {
        String value = source.trim();
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
