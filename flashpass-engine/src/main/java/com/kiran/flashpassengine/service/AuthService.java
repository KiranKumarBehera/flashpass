package com.kiran.flashpassengine.service;

import com.kiran.flashpassengine.exception.SeatUnavailableException;
import com.kiran.flashpassengine.model.User;
import com.kiran.flashpassengine.model.UserRole;
import com.kiran.flashpassengine.repository.UserRepository;
import com.kiran.flashpassengine.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepository, JwtService jwtService, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
    }

    public static String legacySha256(String password) {
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

    public static String hashPassword(String password) {
        return legacySha256(password);
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
                passwordEncoder.encode(password),
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

        boolean matches = passwordEncoder.matches(password, user.getPasswordHash());
        if (!matches) {
            // Check legacy SHA-256 hash and migrate to BCrypt automatically
            String sha256Attempt = legacySha256(password);
            if (sha256Attempt.equals(user.getPasswordHash())) {
                matches = true;
                user.setPasswordHash(passwordEncoder.encode(password));
                userRepository.save(user);
            }
        }

        if (!matches) {
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

        // Generate genuine cryptographically-signed JWT
        String token = jwtService.generateToken(user);
        res.put("token", token);
        return res;
    }
}
