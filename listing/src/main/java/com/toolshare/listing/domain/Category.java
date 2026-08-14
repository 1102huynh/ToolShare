package com.toolshare.listing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

@Entity
@Table(
        name = "categories",
        indexes = {
                @Index(name = "idx_categories_key", columnList = "category_key", unique = true)
        }
)
public class Category {
    private static final Pattern KEY_PATTERN = Pattern.compile("^[a-z0-9]+(?:-[a-z0-9]+)*$");

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "category_key", nullable = false, unique = true)
    private String key;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Category() {
    }

    public Category(String key, String displayName) {
        this.key = normalizeKey(key);
        this.displayName = normalizeDisplayName(displayName);
        this.createdAt = OffsetDateTime.now();
        this.updatedAt = this.createdAt;
    }

    public UUID getId() {
        return id;
    }

    public String getKey() {
        return key;
    }

    public String getDisplayName() {
        return displayName;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void update(String key, String displayName) {
        this.key = normalizeKey(key);
        this.displayName = normalizeDisplayName(displayName);
        this.updatedAt = OffsetDateTime.now();
    }

    public static String normalizeKey(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Category key is required");
        }

        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("Category key is required");
        }
        if (!KEY_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Category key format is invalid");
        }
        return normalized;
    }

    public static String normalizeDisplayName(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Category display name is required");
        }

        String normalized = value.trim();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("Category display name is required");
        }
        return normalized;
    }
}