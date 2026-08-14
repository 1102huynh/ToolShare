package com.toolshare.listing.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(
        name = "tool_listings",
        indexes = {
                @Index(name = "idx_tool_listings_owner_account", columnList = "owner_account_id"),
                @Index(name = "idx_tool_listings_category", columnList = "category_id"),
                @Index(name = "idx_tool_listings_status", columnList = "status")
        }
)
public class ToolListing {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "owner_account_id", nullable = false)
    private UUID ownerAccountId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @OneToOne(optional = false, cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "location_id", nullable = false)
    private ListingLocation location;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ListingStatus status;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, length = 4000)
    private String description;

    @Column(name = "daily_rate_amount", nullable = false)
    private long dailyRateAmount;

    @Column(name = "deposit_amount", nullable = false)
    private long depositAmount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "delivery_available", nullable = false)
    private boolean deliveryAvailable;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @OneToMany(mappedBy = "toolListing", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ToolImage> images = new ArrayList<>();

    protected ToolListing() {
    }

    public ToolListing(
            UUID ownerAccountId,
            Category category,
            ListingLocation location,
            String title,
            String description,
            long dailyRateAmount,
            long depositAmount,
            String currency,
            boolean deliveryAvailable,
            List<ToolImage> images
    ) {
        this.ownerAccountId = requireOwnerAccountId(ownerAccountId);
        this.category = Objects.requireNonNull(category, "category");
        this.location = Objects.requireNonNull(location, "location");
        this.title = requireText(title, "Listing title is required");
        this.description = requireText(description, "Listing description is required");
        this.dailyRateAmount = requireMoneyAmount(dailyRateAmount, "Daily rate amount must be zero or greater");
        this.depositAmount = requireMoneyAmount(depositAmount, "Deposit amount must be zero or greater");
        this.currency = normalizeCurrency(currency);
        this.deliveryAvailable = deliveryAvailable;
        this.status = ListingStatus.DRAFT;
        this.createdAt = OffsetDateTime.now();
        this.updatedAt = this.createdAt;
        replaceImages(images == null ? List.of() : images);
    }

    public UUID getId() {
        return id;
    }

    public UUID getOwnerAccountId() {
        return ownerAccountId;
    }

    public Category getCategory() {
        return category;
    }

    public ListingLocation getLocation() {
        return location;
    }

    public ListingStatus getStatus() {
        return status;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public long getDailyRateAmount() {
        return dailyRateAmount;
    }

    public long getDepositAmount() {
        return depositAmount;
    }

    public String getCurrency() {
        return currency;
    }

    public boolean isDeliveryAvailable() {
        return deliveryAvailable;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public List<ToolImage> getImages() {
        return Collections.unmodifiableList(images);
    }

    public void update(
            Category category,
            ListingLocation location,
            String title,
            String description,
            long dailyRateAmount,
            long depositAmount,
            String currency,
            boolean deliveryAvailable,
            List<ToolImage> images
    ) {
        this.category = Objects.requireNonNull(category, "category");
        this.location = Objects.requireNonNull(location, "location");
        this.title = requireText(title, "Listing title is required");
        this.description = requireText(description, "Listing description is required");
        this.dailyRateAmount = requireMoneyAmount(dailyRateAmount, "Daily rate amount must be zero or greater");
        this.depositAmount = requireMoneyAmount(depositAmount, "Deposit amount must be zero or greater");
        this.currency = normalizeCurrency(currency);
        this.deliveryAvailable = deliveryAvailable;
        replaceImages(images == null ? List.of() : images);
        this.updatedAt = OffsetDateTime.now();
    }

    public void submitForReview() {
        transitionFrom(ListingStatus.DRAFT, ListingStatus.PENDING_REVIEW);
    }

    public void activate() {
        transitionFrom(ListingStatus.PENDING_REVIEW, ListingStatus.ACTIVE);
    }

    public void pause() {
        transitionFrom(ListingStatus.ACTIVE, ListingStatus.PAUSED);
    }

    public void suspend() {
        transitionFrom(ListingStatus.ACTIVE, ListingStatus.SUSPENDED);
    }

    public void archive() {
        transitionFrom(ListingStatus.ACTIVE, ListingStatus.ARCHIVED);
    }

    private void transitionFrom(ListingStatus expectedCurrent, ListingStatus next) {
        if (status != expectedCurrent) {
            throw new IllegalStateException("Listing cannot transition from " + status + " to " + next);
        }
        status = next;
        updatedAt = OffsetDateTime.now();
    }

    private void replaceImages(List<ToolImage> nextImages) {
        images.clear();
        for (ToolImage image : nextImages) {
            ToolImage ownedImage = Objects.requireNonNull(image, "image");
            ownedImage.assignTo(this);
            images.add(ownedImage);
        }
    }

    private static UUID requireOwnerAccountId(UUID value) {
        return Objects.requireNonNull(value, "ownerAccountId");
    }

    private static String requireText(String value, String message) {
        if (value == null) {
            throw new IllegalArgumentException(message);
        }

        String normalized = value.trim();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    private static long requireMoneyAmount(long value, String message) {
        if (value < 0) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private static String normalizeCurrency(String value) {
        String normalized = requireText(value, "Currency is required").toUpperCase(Locale.ROOT);
        if (normalized.length() != 3) {
            throw new IllegalArgumentException("Currency must be a 3-letter code");
        }
        return normalized;
    }
}