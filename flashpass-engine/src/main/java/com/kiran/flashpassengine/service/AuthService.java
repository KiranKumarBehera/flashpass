package com.kiran.flashpassengine.service;

import com.kiran.flashpassengine.exception.SeatUnavailableException;
import com.kiran.flashpassengine.model.User;
import com.kiran.flashpassengine.model.UserRole;
import com.kiran.flashpassengine.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

@Service
public class AuthService {

    private final UserRepository userRepository;

    public AuthService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public static String hashPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedhash = digest.digest(password.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder(2 * encodedhash.length);
            for (byte b : encodedhash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    public Map<String, Object> register(String username, String email, String password, String fullName, UserRole role) {
        if (username == null || username.trim().isEmpty()) {
            throw new SeatUnavailableException("Username is required.");
        }
        if (password == null || password.length() < 4) {
            throw new SeatUnavailableException("Password must be at least 4 characters.");
        }
        if (userRepository.existsByUsername(username.trim())) {
            throw new SeatUnavailableException("Username '" + username + "' is already taken.");
        }
        if (email != null && !email.trim().isEmpty() && userRepository.existsByEmail(email.trim())) {
            throw new SeatUnavailableException("Email '" + email + "' is already registered.");
        }

        User user = new User(
                username.trim(),
                email != null && !email.trim().isEmpty() ? email.trim() : username.trim() + "@flashpass.io",
                hashPassword(password),
                fullName != null ? fullName.trim() : username.trim(),
                role != null ? role : UserRole.ROLE_FAN
        );

        User saved = userRepository.save(user);
        return buildAuthResponse(saved);
    }

    public Map<String, Object> login(String username, String password) {
        if (username == null || password == null) {
            throw new SeatUnavailableException("Username and password are required.");
        }

        User user = userRepository.findByUsername(username.trim())
                .orElseThrow(() -> new SeatUnavailableException("Invalid username or password."));

        String hashedAttempt = hashPassword(password);
        if (!hashedAttempt.equals(user.getPasswordHash())) {
            throw new SeatUnavailableException("Invalid username or password.");
        }

        return buildAuthResponse(user);
    }

    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    private Map<String, Object> buildAuthResponse(User user) {
        Map<String, Object> res = new HashMap<>();
        res.put("id", user.getId());
        res.put("username", user.getUsername());
        res.put("email", user.getEmail());
        res.put("fullName", user.getFullName());
        res.put("role", user.getRole().name());
        // Simple base64 token encoding user:role:timestamp
        String rawToken = user.getUsername() + ":" + user.getRole().name() + ":" + System.currentTimeMillis();
        String token = Base64.getEncoder().encodeToString(rawToken.getBytes(StandardCharsets.UTF_8));
        res.put("token", token);
        return res;
    }
}
