package com.toolshare.booking;

import com.toolshare.common.config.ToolShareConfiguration;
import com.toolshare.common.config.ToolShareJacksonConfig;
import com.toolshare.common.exception.ToolShareExceptionHandler;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@ComponentScan(basePackageClasses = {
        BookingApplication.class,
        ToolShareConfiguration.class,
        ToolShareJacksonConfig.class,
        ToolShareExceptionHandler.class
})
@EntityScan(basePackages = {
        "com.toolshare.identity.domain",
        "com.toolshare.listing.domain",
        "com.toolshare.booking.domain"
})
@EnableJpaRepositories(basePackages = {
        "com.toolshare.identity.infrastructure.persistence",
        "com.toolshare.listing.infrastructure.persistence",
        "com.toolshare.booking.infrastructure.persistence"
})
public class BookingApplication {
    public static void main(String[] args) {
        SpringApplication.run(BookingApplication.class, args);
    }
}
