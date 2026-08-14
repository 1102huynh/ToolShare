package com.toolshare.listing.infrastructure.persistence;

import com.toolshare.listing.domain.ToolListing;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ToolListingRepository extends JpaRepository<ToolListing, UUID> {
    Optional<ToolListing> findByIdAndOwnerAccountId(UUID id, UUID ownerAccountId);
}