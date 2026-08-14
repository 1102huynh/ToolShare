package com.toolshare.common.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ToolShareProperties.class)
public class ToolShareConfiguration {
}
