package com.adgroup.aicodereview.auth;

import com.adgroup.aicodereview.service.AuthService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class AdminBootstrap implements CommandLineRunner {
    private final AuthService authService;
    private final String adminName;
    private final String adminEmail;
    private final String adminPassword;

    public AdminBootstrap(
            AuthService authService,
            @Value("${app.admin.name:Administrator}") String adminName,
            @Value("${app.admin.email:}") String adminEmail,
            @Value("${app.admin.password:}") String adminPassword) {
        this.authService = authService;
        this.adminName = adminName;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
    }

    @Override
    public void run(String... args) {
        if (adminEmail.isBlank() || adminPassword.isBlank()) {
            return;
        }
        authService.provisionAdmin(adminName, adminEmail, adminPassword);
    }
}
