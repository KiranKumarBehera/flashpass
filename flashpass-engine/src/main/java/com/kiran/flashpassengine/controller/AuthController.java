package com.kiran.flashpassengine.controller;

import com.kiran.flashpassengine.model.User;
import com.kiran.flashpassengine.model.UserRole;
import com.kiran.flashpassengine.service.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
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
    public ResponseEntity<?> getMe(Authentication authentication, @RequestParam(required = false) String username) {
        String targetUser = (authentication != null && authentication.isAuthenticated())
                ? authentication.getName()
                : username;

        if (targetUser == null || targetUser.isBlank() || "anonymousUser".equalsIgnoreCase(targetUser)) {
            return ResponseEntity.status(401).body(Map.of(
                    "status", 401,
                    "error", "UNAUTHORIZED",
                    "message", "Authentication required. Please provide a valid Bearer JWT."
            ));
        }

        return authService.findByUsername(targetUser)
                .map(user -> ResponseEntity.ok(Map.of(
                        "id", user.getId(),
                        "username", user.getUsername(),
                        "email", user.getEmail(),
                        "fullName", user.getFullName(),
                        "role", user.getRole().name()
                )))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/users")
    public ResponseEntity<List<User>> getAllUsers() {
        return ResponseEntity.ok(authService.getAllUsers());
    }
}
