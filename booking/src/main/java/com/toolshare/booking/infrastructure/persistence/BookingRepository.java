package com.toolshare.booking.infrastructure.persistence;

import com.toolshare.booking.domain.Booking;
import com.toolshare.booking.domain.BookingState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface BookingRepository extends JpaRepository<Booking, UUID> {

    @Query("""
            select b
            from Booking b
            where b.listingId = :listingId
              and b.state in :states
              and b.requestedStartAt < :requestedEnd
              and b.requestedEndAt > :requestedStart
            """)
    List<Booking> findOverlappingByListingAndStates(
            @Param("listingId") UUID listingId,
            @Param("requestedStart") LocalDateTime requestedStart,
            @Param("requestedEnd") LocalDateTime requestedEnd,
            @Param("states") List<BookingState> states
    );
}
