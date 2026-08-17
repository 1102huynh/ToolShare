package com.toolshare.listing.infrastructure.persistence;

import com.toolshare.listing.domain.ListingStatus;
import com.toolshare.listing.domain.ToolListing;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ToolListingRepository extends JpaRepository<ToolListing, UUID> {
    List<ToolListing> findAllByStatus(ListingStatus status);

    Optional<ToolListing> findByIdAndOwnerAccountId(UUID id, UUID ownerAccountId);

    /**
     * Locks the listing row for the duration of the caller's transaction so that
     * concurrent booking-creation attempts for the same listing serialize on this
     * row instead of racing between the overlap check and the booking insert.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from ToolListing t where t.id = :id")
    Optional<ToolListing> findByIdForUpdate(@Param("id") UUID id);
}