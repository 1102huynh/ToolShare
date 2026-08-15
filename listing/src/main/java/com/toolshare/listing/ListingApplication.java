package com.toolshare.listing;

import com.toolshare.common.config.ToolShareConfiguration;
import com.toolshare.common.config.ToolShareJacksonConfig;
import com.toolshare.common.exception.ToolShareExceptionHandler;
import com.toolshare.identity.authorization.AuthorizationService;
import com.toolshare.identity.infrastructure.security.SecurityConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@ConfigurationPropertiesScan(basePackages = "com.toolshare.listing")
@ComponentScan(basePackageClasses = {
        ListingApplication.class,
        ToolShareConfiguration.class,
        ToolShareJacksonConfig.class,
        ToolShareExceptionHandler.class,
        SecurityConfig.class,
        AuthorizationService.class
})
@EntityScan(basePackages = {
        "com.toolshare.identity.domain",
        "com.toolshare.listing.domain"
})
@EnableJpaRepositories(basePackages = {
        "com.toolshare.identity.infrastructure.persistence",
        "com.toolshare.listing.infrastructure.persistence"
})
public class ListingApplication {
    public static void main(String[] args) {
        SpringApplication.run(ListingApplication.class, args);
    }
}