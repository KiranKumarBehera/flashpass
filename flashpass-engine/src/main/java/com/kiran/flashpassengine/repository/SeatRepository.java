package com.kiran.flashpassengine.repository;

import com.kiran.flashpassengine.model.Seat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface SeatRepository extends JpaRepository<Seat, Long> {

    // Spring Data JPA automatically converts this method name into SQL!
    // Equivalent to: SELECT * FROM seats WHERE event_id = ? ORDER BY seat_number ASC;
    List<Seat> findByEventIdOrderBySeatNumberAsc(Long eventId);
}