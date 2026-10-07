package com.kiran.flashpassengine.controller;

import com.kiran.flashpassengine.model.User;
import com.kiran.flashpassengine.model.UserRole;
import com.kiran.flashpassengine.service.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String email = body.get("email");
        String password = body.get("password");
        String fullName = body.get("fullName");
        String roleStr = body.getOrDefault("role", "ROLE_FAN");
        UserRole role = "ROLE_ORGANIZER".equalsIgnoreCase(roleStr) ? UserRole.ROLE_ORGANIZER : UserRole.ROLE_FAN;

        return ResponseEntity.ok(authService.register(username, email, password, fullName, role));
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");
        return ResponseEntity.ok(authService.login(username, password));
    }

    @GetMapping("/me")
    public ResponseEntity<?> getMe(@RequestParam(required = false) String username) {
        if (username == null || username.isBlank()) {
            return ResponseEntity.badRequest().body("Username parameter is required.");
        }
        return authService.findByUsername(username)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/users")
    public ResponseEntity<List<User>> getAllUsers() {
        return ResponseEntity.ok(authService.getAllUsers());
    }
}
