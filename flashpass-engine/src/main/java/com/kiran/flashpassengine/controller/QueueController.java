package com.kiran.flashpassengine.controller;

import com.kiran.flashpassengine.service.VirtualQueueService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/queue")
@CrossOrigin(origins = "*")
public class QueueController {

    private final VirtualQueueService queueService;

    public QueueController(VirtualQueueService queueService) {
        this.queueService = queueService;
    }

    /**
     * 🎟️ Join the Virtual Waiting Room for high-demand concert
     */
    @PostMapping("/join")
    public ResponseEntity<Map<String, Object>> joinQueue(
            @RequestParam Long eventId,
            @RequestParam(required = false, defaultValue = "fan") String user) {
        return ResponseEntity.ok(queueService.joinQueue(eventId, user));
    }

    /**
     * ⏱️ Poll live position in queue
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getQueueStatus(
            @RequestParam Long eventId,
            @RequestParam(required = false, defaultValue = "fan") String user) {
        return ResponseEntity.ok(queueService.getQueueStatus(eventId, user));
    }

    /**
     * ⚡ 100,000-User Surge Stress Test Benchmark
     */
    @PostMapping("/simulate-surge")
    public ResponseEntity<Map<String, Object>> simulateSurge(
            @RequestParam Long eventId,
            @RequestParam(required = false, defaultValue = "100000") Integer users) {
        return ResponseEntity.ok(queueService.simulate100kSurge(eventId, users));
    }
}
