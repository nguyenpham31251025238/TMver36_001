package com.example.tmver36.model;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

public class AppUser {
    private static final int ITERATIONS = 120_000;
    private static final int KEY_LENGTH = 256;
    private static final SecureRandom RANDOM = new SecureRandom();

    private String username;
    private String passwordHash;
    private String role;
    private String fullName;
    private boolean active;

    public AppUser(String username, String password, String role, String fullName) {
        this(username, hashPassword(password), role, fullName, true, true);
    }

    private AppUser(String username, String passwordHash, String role, String fullName, boolean active, boolean alreadyHashed) {
        this.username = username;
        this.passwordHash = alreadyHashed ? passwordHash : hashPassword(passwordHash);
        this.role = role;
        this.fullName = fullName;
        this.active = active;
    }

    public static AppUser fromStoredHash(String username, String passwordHash, String role, String fullName, boolean active) {
        return new AppUser(username, passwordHash, role, fullName, active, true);
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPassword(String plainPassword) {
        if (plainPassword != null && !plainPassword.isBlank()) {
            this.passwordHash = hashPassword(plainPassword);
        }
    }

    public boolean verifyPassword(String candidate) {
        if (candidate == null || passwordHash == null) {
            return false;
        }
        if (!passwordHash.contains(":")) {
            boolean matched = MessageDigest.isEqual(
                    passwordHash.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    candidate.getBytes(java.nio.charset.StandardCharsets.UTF_8)
            );
            if (matched) {
                this.passwordHash = hashPassword(candidate);
            }
            return matched;
        }
        try {
            String[] parts = passwordHash.split(":", 2);
            byte[] salt = Base64.getDecoder().decode(parts[0]);
            byte[] expected = Base64.getDecoder().decode(parts[1]);
            PBEKeySpec spec = new PBEKeySpec(candidate.toCharArray(), salt, ITERATIONS, KEY_LENGTH);
            try {
                byte[] actual = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
                return MessageDigest.isEqual(expected, actual);
            } finally {
                spec.clearPassword();
            }
        } catch (IllegalArgumentException | GeneralSecurityException ex) {
            return false;
        }
    }

    public static String hashPassword(String password) {
        String safePassword = password == null ? "" : password;
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        PBEKeySpec spec = new PBEKeySpec(safePassword.toCharArray(), salt, ITERATIONS, KEY_LENGTH);
        try {
            byte[] hash = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            return Base64.getEncoder().encodeToString(salt) + ":" + Base64.getEncoder().encodeToString(hash);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Password hashing is unavailable", ex);
        } finally {
            spec.clearPassword();
        }
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
