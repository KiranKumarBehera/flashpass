package com.kiran.flashpassengine.repository;

import com.kiran.flashpassengine.model.Event;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EventRepository extends JpaRepository<Event, Long> {
    // JpaRepository gives us: save(), findById(), findAll(), delete(), etc.
}