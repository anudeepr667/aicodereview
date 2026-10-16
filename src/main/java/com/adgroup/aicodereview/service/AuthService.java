package com.adgroup.aicodereview.service;

import com.adgroup.aicodereview.dto.RegisterRequest;
import com.adgroup.aicodereview.model.Role;
import com.adgroup.aicodereview.model.User;
import com.adgroup.aicodereview.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public User register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new DuplicateEmailException();
        }

        User user = new User(
                request.name().trim(),
                email,
                passwordEncoder.encode(request.password()),
                Role.USER
        );
        return userRepository.save(user);
    }

    public User provisionAdmin(String name, String email, String password) {
        String normalizedEmail = normalizeEmail(email);
        User user = userRepository.findByEmailIgnoreCase(normalizedEmail)
                .orElseGet(() -> new User(name.trim(), normalizedEmail, passwordEncoder.encode(password), Role.ADMIN));
        user.setRole(Role.ADMIN);
        user.setPassword(passwordEncoder.encode(password));
        return userRepository.save(user);
    }

    public static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(java.util.Locale.ROOT);
    }

    public static class DuplicateEmailException extends RuntimeException {
    }
}
