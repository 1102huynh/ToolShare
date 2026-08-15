package com.toolshare.listing.api;

import com.toolshare.listing.api.dto.ListingImageUploadResponse;
import com.toolshare.listing.application.ListingImageUploadService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/listings")
public class ListingImageUploadController {

    private final ListingImageUploadService listingImageUploadService;

    public ListingImageUploadController(ListingImageUploadService listingImageUploadService) {
        this.listingImageUploadService = listingImageUploadService;
    }

    @PostMapping(value = "/{listingId}/images", consumes = "multipart/form-data")
    public ResponseEntity<ListingImageUploadResponse> upload(
            @PathVariable UUID listingId,
            @RequestPart(value = "file", required = false) MultipartFile file
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ListingImageUploadResponse(listingImageUploadService.upload(listingId, file)));
    }
}