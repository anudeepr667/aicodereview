package com.adgroup.aicodereview;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.adgroup.aicodereview.model.Role;
import com.adgroup.aicodereview.model.User;
import com.adgroup.aicodereview.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
class AuthenticationIntegrationTests {
    private static final String FRONTEND_ORIGIN = "http://localhost:5173";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void registrationHashesPasswordAndDoesNotAcceptRoleEscalation() throws Exception {
        String email = uniqueEmail();
        mockMvc.perform(post("/api/auth/register")
                        .with(csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Test User\",\"email\":\"" + email + "\",\"password\":\"password123\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role", is("USER")))
                .andExpect(jsonPath("$.password").doesNotExist());

        User user = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        org.junit.jupiter.api.Assertions.assertNotEquals("password123", user.getPassword());
        org.junit.jupiter.api.Assertions.assertTrue(passwordEncoder.matches("password123", user.getPassword()));
    }

    @Test
    void anonymousRegistrationAcceptsTokenFromCsrfEndpoint() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .with(csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"New User\",\"email\":\"" + uniqueEmail() + "\",\"password\":\"password123\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void anonymousRegistrationAcceptsRawCookieToken() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .with(csrfToken(true))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"New User\",\"email\":\"" + uniqueEmail() + "\",\"password\":\"password123\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void anonymousRegistrationRequiresCsrfToken() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"New User\",\"email\":\"" + uniqueEmail() + "\",\"password\":\"password123\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void duplicateRegistrationIsRejected() throws Exception {
        String email = uniqueEmail();
        userRepository.save(new User("Existing", email, passwordEncoder.encode("password123"), Role.USER));

        mockMvc.perform(post("/api/auth/register")
                        .with(csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Another\",\"email\":\"" + email + "\",\"password\":\"password123\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void invalidRegistrationReturnsValidationMessage() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .with(csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"email\":\"invalid\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void loginMeAndLogoutManageTheSession() throws Exception {
        String email = uniqueEmail();
        userRepository.save(new User("Session User", email, passwordEncoder.encode("password123"), Role.USER));

        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .with(csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email", is(email)))
                .andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);

        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role", is("USER")));

        mockMvc.perform(post("/api/auth/logout").with(csrfToken()).session(session))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidCredentialsAreRejected() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .with(csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"missing@example.com\",\"password\":\"wrongpassword\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void onlyAdminsCanAccessAdminEndpoints() throws Exception {
        mockMvc.perform(get("/api/admin/status"))
                .andExpect(status().isUnauthorized());

        String adminEmail = uniqueEmail();
        userRepository.save(new User("Admin", adminEmail, passwordEncoder.encode("password123"), Role.ADMIN));
        MockHttpSession adminSession = login(adminEmail);
        mockMvc.perform(get("/api/admin/status").session(adminSession))
                .andExpect(status().isOk());

        String userEmail = uniqueEmail();
        userRepository.save(new User("User", userEmail, passwordEncoder.encode("password123"), Role.USER));
        MockHttpSession userSession = login(userEmail);
        mockMvc.perform(get("/api/admin/status").session(userSession))
                .andExpect(status().isForbidden());
    }

    @Test
    void corsPreflightAllowsConfiguredFrontendOrigin() throws Exception {
        mockMvc.perform(options("/api/auth/register")
                        .header("Origin", FRONTEND_ORIGIN)
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "content-type,x-xsrf-token"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().string("Access-Control-Allow-Origin", FRONTEND_ORIGIN))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().string("Access-Control-Allow-Credentials", "true"));
    }

    private MockHttpSession login(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .with(csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private RequestPostProcessor csrfToken() throws Exception {
        return csrfToken(false);
    }

    private RequestPostProcessor csrfToken(boolean useCookieValue) throws Exception {
        MvcResult csrfResult = mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        String maskedToken = JsonPath.read(csrfResult.getResponse().getContentAsString(), "$.token");
        Cookie cookie = csrfResult.getResponse().getCookie("XSRF-TOKEN");
        org.junit.jupiter.api.Assertions.assertNotNull(cookie);
        String token = useCookieValue ? cookie.getValue() : maskedToken;
        return request -> {
            request.setCookies(cookie);
            request.addHeader("X-XSRF-TOKEN", token);
            return request;
        };
    }

    private String uniqueEmail() {
        return "auth-" + UUID.randomUUID() + "@example.com";
    }
}
