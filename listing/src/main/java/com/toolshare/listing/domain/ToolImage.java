package com.toolshare.listing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "tool_images")
public class ToolImage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tool_listing_id", nullable = false)
    private ToolListing toolListing;

    @Column(name = "storage_key", nullable = false)
    private String storageKey;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected ToolImage() {
    }

    public ToolImage(String storageKey, int displayOrder) {
        this.storageKey = requireStorageKey(storageKey);
        this.displayOrder = requireDisplayOrder(displayOrder);
        this.createdAt = OffsetDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public ToolListing getToolListing() {
        return toolListing;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    void assignTo(ToolListing toolListing) {
        this.toolListing = toolListing;
    }

    public static String requireStorageKey(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Image storage key is required");
        }

        String normalized = value.trim();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("Image storage key is required");
        }
        return normalized;
    }

    public static int requireDisplayOrder(int value) {
        if (value < 0) {
            throw new IllegalArgumentException("Image display order must be zero or greater");
        }
        return value;
    }
}