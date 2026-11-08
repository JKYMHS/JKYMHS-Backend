package com.jkymhs.backend.global.config;

import com.jkymhs.backend.global.web.CodeEnumConverterFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    // 쿼리 파라미터의 FE 소문자 enum 값(예: tab=festival)을 CodeEnum 상수로 바꾸는 변환기를 등록한다
    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverterFactory(new CodeEnumConverterFactory());
    }
}
