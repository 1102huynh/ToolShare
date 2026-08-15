package com.toolshare.listing.application;

import com.toolshare.common.exception.ForbiddenException;
import com.toolshare.common.exception.ResourceNotFoundException;
import com.toolshare.identity.authorization.AuthorizationService;
import com.toolshare.identity.authorization.Permission;
import com.toolshare.identity.domain.IdentityAccount;
import com.toolshare.listing.domain.ToolListing;
import com.toolshare.listing.infrastructure.persistence.ToolListingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@Service
public class ListingImageUploadService {

    private final ToolListingRepository toolListingRepository;
    private final AuthenticatedAccountResolver authenticatedAccountResolver;
    private final AuthorizationService authorizationService;
    private final ListingImageUploadValidator validator;
    private final FileUploadScanner fileUploadScanner;
    private final FileStorage fileStorage;

    public ListingImageUploadService(
            ToolListingRepository toolListingRepository,
            AuthenticatedAccountResolver authenticatedAccountResolver,
            AuthorizationService authorizationService,
            ListingImageUploadValidator validator,
            FileUploadScanner fileUploadScanner,
            FileStorage fileStorage
    ) {
        this.toolListingRepository = toolListingRepository;
        this.authenticatedAccountResolver = authenticatedAccountResolver;
        this.authorizationService = authorizationService;
        this.validator = validator;
        this.fileUploadScanner = fileUploadScanner;
        this.fileStorage = fileStorage;
    }

    @Transactional(readOnly = true)
    public String upload(UUID listingId, MultipartFile file) {
        IdentityAccount account = requireOwner();
        requireOwnedListing(listingId, account);

        validator.validate(file);

        try {
            byte[] content = file.getBytes();
            fileUploadScanner.scan(content, file.getContentType());
            return fileStorage.storeListingImage(listingId, content, file.getContentType());
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read uploaded file", ex);
        }
    }

    private IdentityAccount requireOwner() {
        IdentityAccount account = authenticatedAccountResolver.requireCurrentAccount();
        if (!authorizationService.hasPermission(account, Permission.OWNER_ACCESS)) {
            throw new ForbiddenException("Owner permission is required");
        }
        return account;
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
}