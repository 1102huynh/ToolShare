package com.toolshare.deposit;

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
        DepositApplication.class,
        ToolShareConfiguration.class,
        ToolShareJacksonConfig.class,
        ToolShareExceptionHandler.class
})
@EntityScan(basePackages = {
        "com.toolshare.identity.domain",
        "com.toolshare.listing.domain",
        "com.toolshare.booking.domain",
        "com.toolshare.deposit.domain"
})
@EnableJpaRepositories(basePackages = {
        "com.toolshare.identity.infrastructure.persistence",
        "com.toolshare.listing.infrastructure.persistence",
        "com.toolshare.booking.infrastructure.persistence",
        "com.toolshare.deposit.infrastructure.persistence"
})
public class DepositApplication {
    public static void main(String[] args) {
        SpringApplication.run(DepositApplication.class, args);
    }
}
