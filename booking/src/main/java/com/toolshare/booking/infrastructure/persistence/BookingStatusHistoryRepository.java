package com.toolshare.booking.infrastructure.persistence;

import com.toolshare.booking.domain.BookingStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BookingStatusHistoryRepository extends JpaRepository<BookingStatusHistory, UUID> {
    List<BookingStatusHistory> findByBookingIdOrderByChangedAtAsc(UUID bookingId);
}
