package com.toolshare.listing.api;

import com.toolshare.listing.api.dto.ListingImageResponse;
import com.toolshare.listing.api.dto.ListingLocationResponse;
import com.toolshare.listing.api.dto.ListingRequest;
import com.toolshare.listing.api.dto.ListingResponse;
import com.toolshare.listing.application.ListingService;
import com.toolshare.listing.domain.ToolImage;
import com.toolshare.listing.domain.ToolListing;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/listings")
public class ListingController {

    private final ListingService listingService;

    public ListingController(ListingService listingService) {
        this.listingService = listingService;
    }

    @PostMapping
    public ResponseEntity<ListingResponse> create(@Valid @RequestBody ListingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(listingService.create(request)));
    }

    @GetMapping("/{listingId}")
    public ResponseEntity<ListingResponse> findById(@PathVariable UUID listingId) {
        return ResponseEntity.ok(toResponse(listingService.findById(listingId)));
    }

    @PatchMapping("/{listingId}")
    public ResponseEntity<ListingResponse> update(@PathVariable UUID listingId, @Valid @RequestBody ListingRequest request) {
        return ResponseEntity.ok(toResponse(listingService.update(listingId, request)));
    }

    @PostMapping("/{listingId}/submit-for-review")
    public ResponseEntity<ListingResponse> submitForReview(@PathVariable UUID listingId) {
        return ResponseEntity.ok(toResponse(listingService.submitForReview(listingId)));
    }

    @PostMapping("/{listingId}/activate")
    public ResponseEntity<ListingResponse> activate(@PathVariable UUID listingId) {
        return ResponseEntity.ok(toResponse(listingService.activate(listingId)));
    }

    @PostMapping("/{listingId}/pause")
    public ResponseEntity<ListingResponse> pause(@PathVariable UUID listingId) {
        return ResponseEntity.ok(toResponse(listingService.pause(listingId)));
    }

    @PostMapping("/{listingId}/suspend")
    public ResponseEntity<ListingResponse> suspend(@PathVariable UUID listingId) {
        return ResponseEntity.ok(toResponse(listingService.suspend(listingId)));
    }

    @PostMapping("/{listingId}/archive")
    public ResponseEntity<ListingResponse> archive(@PathVariable UUID listingId) {
        return ResponseEntity.ok(toResponse(listingService.archive(listingId)));
    }

    private ListingResponse toResponse(ToolListing listing) {
        return new ListingResponse(
                listing.getId(),
                listing.getOwnerAccountId(),
                listing.getCategory().getId(),
                listing.getStatus(),
                listing.getTitle(),
                listing.getDescription(),
                listing.getDailyRateAmount(),
                listing.getDepositAmount(),
                listing.getCurrency(),
                listing.isDeliveryAvailable(),
                new ListingLocationResponse(
                        listing.getLocation().getId(),
                        listing.getLocation().getAddressLine1(),
                        listing.getLocation().getAddressLine2(),
                        listing.getLocation().getWard(),
                        listing.getLocation().getDistrict(),
                        listing.getLocation().getCity(),
                        listing.getLocation().getCountryCode(),
                        listing.getLocation().getCreatedAt(),
                        listing.getLocation().getUpdatedAt()
                ),
                listing.getImages().stream().map(this::toImageResponse).toList(),
                listing.getCreatedAt(),
                listing.getUpdatedAt()
        );
    }

    private ListingImageResponse toImageResponse(ToolImage image) {
        return new ListingImageResponse(image.getId(), image.getStorageKey(), image.getDisplayOrder(), image.getCreatedAt());
    }
}