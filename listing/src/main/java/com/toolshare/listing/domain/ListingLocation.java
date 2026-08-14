package com.toolshare.listing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "locations")
public class ListingLocation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "address_line_1", nullable = false)
    private String addressLine1;

    @Column(name = "address_line_2")
    private String addressLine2;

    @Column(nullable = false)
    private String ward;

    @Column(nullable = false)
    private String district;

    @Column(nullable = false)
    private String city;

    @Column(name = "country_code", nullable = false, length = 2)
    private String countryCode;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected ListingLocation() {
    }

    public ListingLocation(String addressLine1, String addressLine2, String ward, String district, String city, String countryCode) {
        this.addressLine1 = requireText(addressLine1, "Address line 1 is required");
        this.addressLine2 = normalizeOptional(addressLine2);
        this.ward = requireText(ward, "Ward is required");
        this.district = requireText(district, "District is required");
        this.city = requireText(city, "City is required");
        this.countryCode = normalizeCountryCode(countryCode);
        this.createdAt = OffsetDateTime.now();
        this.updatedAt = this.createdAt;
    }

    public UUID getId() {
        return id;
    }

    public String getAddressLine1() {
        return addressLine1;
    }

    public String getAddressLine2() {
        return addressLine2;
    }

    public String getWard() {
        return ward;
    }

    public String getDistrict() {
        return district;
    }

    public String getCity() {
        return city;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void update(String addressLine1, String addressLine2, String ward, String district, String city, String countryCode) {
        this.addressLine1 = requireText(addressLine1, "Address line 1 is required");
        this.addressLine2 = normalizeOptional(addressLine2);
        this.ward = requireText(ward, "Ward is required");
        this.district = requireText(district, "District is required");
        this.city = requireText(city, "City is required");
        this.countryCode = normalizeCountryCode(countryCode);
        this.updatedAt = OffsetDateTime.now();
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

    private static String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();
        return normalized.isBlank() ? null : normalized;
    }

    private static String normalizeCountryCode(String value) {
        String normalized = requireText(value, "Country code is required").toUpperCase(Locale.ROOT);
        if (normalized.length() != 2) {
            throw new IllegalArgumentException("Country code must be 2 characters");
        }
        return normalized;
    }
}