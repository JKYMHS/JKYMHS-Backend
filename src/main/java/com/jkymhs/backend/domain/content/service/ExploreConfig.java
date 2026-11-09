package com.jkymhs.backend.domain.content.service;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 탐색·상세 설정값({@code explore.*}, {@code content-detail.*})을 record로 읽어 빈으로 등록한다.
 */
@Configuration
@EnableConfigurationProperties({ExploreProperties.class, ContentDetailProperties.class})
public class ExploreConfig {
}
