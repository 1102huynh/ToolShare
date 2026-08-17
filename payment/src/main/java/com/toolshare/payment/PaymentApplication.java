package com.toolshare.payment;

import com.toolshare.common.config.ToolShareConfiguration;
import com.toolshare.common.config.ToolShareJacksonConfig;
import com.toolshare.common.exception.ToolShareExceptionHandler;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * {@code exclude}: {@code payment} depends on {@code identity}, which brings
 * {@code spring-boot-starter-security} onto the classpath transitively. That was
 * inert while {@code payment} had no servlet stack (T-011), but T-012 adds
 * {@code spring-boot-starter-web} for the webhook controller — and with both on
 * the classpath, Spring Boot auto-configures a default security filter chain that
 * would 403 every request, including the webhook endpoint. Per the T-012 plan §12,
 * the webhook endpoint is deliberately NOT behind identity's JWT filter chain (its
 * caller is an external gateway, not a logged-in user) and payment's context never
 * imports {@code identity.infrastructure.security.SecurityConfig} — so the correct
 * fix is to keep Spring Security's auto-configuration out of this application
 * entirely, not to wire up a filter chain and then bypass it.
 */
@SpringBootApplication(exclude = {
        SecurityAutoConfiguration.class,
        UserDetailsServiceAutoConfiguration.class
})
@ComponentScan(basePackageClasses = {
        PaymentApplication.class,
        ToolShareConfiguration.class,
        ToolShareJacksonConfig.class,
        ToolShareExceptionHandler.class,
        com.toolshare.booking.application.BookingService.class
})
@EntityScan(basePackages = {
        "com.toolshare.identity.domain",
        "com.toolshare.listing.domain",
        "com.toolshare.booking.domain",
        "com.toolshare.payment.domain"
})
@EnableJpaRepositories(basePackages = {
        "com.toolshare.identity.infrastructure.persistence",
        "com.toolshare.listing.infrastructure.persistence",
        "com.toolshare.booking.infrastructure.persistence",
        "com.toolshare.payment.infrastructure.persistence"
})
public class PaymentApplication {
    public static void main(String[] args) {
        SpringApplication.run(PaymentApplication.class, args);
    }
}
