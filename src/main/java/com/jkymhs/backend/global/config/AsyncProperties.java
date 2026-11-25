package com.jkymhs.backend.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "async")
public record AsyncProperties(@DefaultValue("true") boolean enabled) {
}
