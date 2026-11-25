package com.jkymhs.backend.domain.content.service;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({ExploreProperties.class, ContentDetailProperties.class})
public class ExploreConfig {
}
