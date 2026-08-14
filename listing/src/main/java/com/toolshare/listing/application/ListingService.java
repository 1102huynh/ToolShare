package com.toolshare.listing.application;

import com.toolshare.common.exception.ConflictException;
import com.toolshare.common.exception.ForbiddenException;
import com.toolshare.common.exception.ResourceNotFoundException;
import com.toolshare.identity.authorization.AuthorizationService;
import com.toolshare.identity.authorization.Permission;
import com.toolshare.identity.domain.IdentityAccount;
import com.toolshare.listing.api.dto.ListingImageRequest;
import com.toolshare.listing.api.dto.ListingLocationRequest;
import com.toolshare.listing.api.dto.ListingRequest;
import com.toolshare.listing.domain.Category;
import com.toolshare.listing.domain.ListingLocation;
import com.toolshare.listing.domain.ToolImage;
import com.toolshare.listing.domain.ToolListing;
import com.toolshare.listing.infrastructure.persistence.CategoryRepository;
import com.toolshare.listing.infrastructure.persistence.ToolListingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class ListingService {

    private final ToolListingRepository toolListingRepository;
    private final CategoryRepository categoryRepository;
    private final AuthenticatedAccountResolver authenticatedAccountResolver;
    private final AuthorizationService authorizationService;

    public ListingService(
            ToolListingRepository toolListingRepository,
            CategoryRepository categoryRepository,
            AuthenticatedAccountResolver authenticatedAccountResolver,
            AuthorizationService authorizationService
    ) {
        this.toolListingRepository = toolListingRepository;
        this.categoryRepository = categoryRepository;
        this.authenticatedAccountResolver = authenticatedAccountResolver;
        this.authorizationService = authorizationService;
    }

    @Transactional
    public ToolListing create(ListingRequest request) {
        IdentityAccount account = requireOwner();
        Category category = requireCategory(request.categoryId());
        ToolListing listing = new ToolListing(
                account.getId(),
                category,
                toLocation(request.location()),
                request.title(),
                request.description(),
                request.dailyRateAmount(),
                request.depositAmount(),
                request.currency(),
                request.deliveryAvailable(),
                toImages(request.images())
        );
        return toolListingRepository.save(listing);
    }

    @Transactional(readOnly = true)
    public ToolListing findById(UUID listingId) {
        authenticatedAccountResolver.requireCurrentAccount();
        return toolListingRepository.findById(listingId)
                .orElseThrow(() -> new ResourceNotFoundException("Listing not found"));
    }

    @Transactional
    public ToolListing update(UUID listingId, ListingRequest request) {
        IdentityAccount account = requireOwner();
        ToolListing listing = requireOwnedListing(listingId, account);
        Category category = requireCategory(request.categoryId());
        listing.update(
                category,
                toLocation(request.location()),
                request.title(),
                request.description(),
                request.dailyRateAmount(),
                request.depositAmount(),
                request.currency(),
                request.deliveryAvailable(),
                toImages(request.images())
        );
        return toolListingRepository.save(listing);
    }

    @Transactional
    public ToolListing submitForReview(UUID listingId) {
        IdentityAccount account = requireOwner();
        ToolListing listing = requireOwnedListing(listingId, account);
        applyTransition(listing::submitForReview);
        return toolListingRepository.save(listing);
    }

    @Transactional
    public ToolListing activate(UUID listingId) {
        IdentityAccount account = requireOwner();
        ToolListing listing = requireOwnedListing(listingId, account);
        applyTransition(listing::activate);
        return toolListingRepository.save(listing);
    }

    @Transactional
    public ToolListing pause(UUID listingId) {
        IdentityAccount account = requireOwner();
        ToolListing listing = requireOwnedListing(listingId, account);
        applyTransition(listing::pause);
        return toolListingRepository.save(listing);
    }

    @Transactional
    public ToolListing suspend(UUID listingId) {
        requireAdmin();
        ToolListing listing = toolListingRepository.findById(listingId)
                .orElseThrow(() -> new ResourceNotFoundException("Listing not found"));
        applyTransition(listing::suspend);
        return toolListingRepository.save(listing);
    }

    @Transactional
    public ToolListing archive(UUID listingId) {
        IdentityAccount account = requireOwner();
        ToolListing listing = requireOwnedListing(listingId, account);
        applyTransition(listing::archive);
        return toolListingRepository.save(listing);
    }

    private void applyTransition(Runnable transition) {
        try {
            transition.run();
        } catch (IllegalStateException ex) {
            throw new ConflictException(ex.getMessage());
        }
    }

    private Category requireCategory(UUID categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));
    }

    private IdentityAccount requireOwner() {
        IdentityAccount account = authenticatedAccountResolver.requireCurrentAccount();
        if (!authorizationService.hasPermission(account, Permission.OWNER_ACCESS)) {
            throw new ForbiddenException("Owner permission is required");
        }
        return account;
    }

    private void requireAdmin() {
        IdentityAccount account = authenticatedAccountResolver.requireCurrentAccount();
        if (!authorizationService.hasPermission(account, Permission.ADMIN_ACCESS)) {
            throw new ForbiddenException("Admin permission is required");
        }
    }

    private ToolListing requireOwnedListing(UUID listingId, IdentityAccount account) {
        return toolListingRepository.findById(listingId)
                .map(listing -> {
                    if (!listing.getOwnerAccountId().equals(account.getId())) {
                        throw new ForbiddenException("Listing ownership is required");
                    }
                    return listing;
                })
                .orElseThrow(() -> new ResourceNotFoundException("Listing not found"));
    }

    private ListingLocation toLocation(ListingLocationRequest request) {
        return new ListingLocation(
                request.addressLine1(),
                request.addressLine2(),
                request.ward(),
                request.district(),
                request.city(),
                request.countryCode()
        );
    }

    private List<ToolImage> toImages(List<ListingImageRequest> requests) {
        if (requests == null) {
            return List.of();
        }
        return requests.stream()
                .map(request -> new ToolImage(request.storageKey(), request.displayOrder()))
                .sorted(Comparator.comparingInt(ToolImage::getDisplayOrder))
                .toList();
    }
}