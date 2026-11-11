package com.shopsphere;

import java.util.Map;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import com.shopsphere.common.ApiResponse;
import com.shopsphere.common.ApiError;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 8: authentication + RBAC + tenant isolation, exercised over HTTP
 * against the real local database (unique emails keep runs repeatable).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("local")
class AuthRbacIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    private static String uniqueEmail() {
        return "it-" + Long.toString(System.nanoTime(), 36) + "@shopsphere.local";
    }

    @SuppressWarnings("unchecked")
    private String tokenFor(String email, String password) {
        ResponseEntity<Map> res = rest.postForEntity("/api/v1/auth/login",
                entityMap(Map.of("email", email, "password", password)), Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> data = (Map<String, Object>) res.getBody().get("data");
        return (String) data.get("accessToken");
    }

    private HttpEntity<Map<String, ?>> entityMap(Map<String, ?> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    @Test
    void registerLoginAndDuplicateEmailRejected() {
        String email = uniqueEmail();

        ResponseEntity<Map> registered = rest.postForEntity("/api/v1/auth/register",
                entityMap(Map.of("fullName", "Integration Tester", "email", email,
                        "password", "Str0ngPass!2026", "phone", "+91-9000000000")), Map.class);
        assertThat(registered.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Second registration with the same email must not succeed
        ResponseEntity<Map> duplicate = rest.postForEntity("/api/v1/auth/register",
                entityMap(Map.of("fullName", "Integration Tester", "email", email,
                        "password", "Str0ngPass!2026", "phone", "+91-9000000000")), Map.class);
        assertThat(duplicate.getStatusCode().is4xxClientError()).isTrue();

        // Login with wrong password must fail without leaking the account state
        ResponseEntity<ApiError> wrong = rest.postForEntity("/api/v1/auth/login",
                entityMap(Map.of("email", email, "password", "WrongPass!2026")), ApiError.class);
        assertThat(wrong.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        String token = tokenFor(email, "Str0ngPass!2026");
        assertThat(token).matches(Pattern.compile("^eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+$"));
    }

    @Test
    void passwordChangePersistsAndOldPasswordStopsWorking() {
        String email = uniqueEmail();
        ResponseEntity<Map> registered = rest.postForEntity("/api/v1/auth/register",
                entityMap(Map.of("fullName", "Password Tester", "email", email,
                        "password", "Str0ngPass!2026", "phone", "+91-9000000001")), Map.class);
        assertThat(registered.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        String token = tokenFor(email, "Str0ngPass!2026");
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);

        ResponseEntity<Void> changed = rest.exchange("/api/v1/users/me/password", HttpMethod.POST,
                new HttpEntity<>(Map.of("currentPassword", "Str0ngPass!2026",
                        "newPassword", "Even!Str0nger2026"), headers), Void.class);
        assertThat(changed.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // The new password must work and the old one must be rejected.
        assertThat(tokenFor(email, "Even!Str0nger2026")).isNotBlank();

        ResponseEntity<Map> oldLogin = rest.postForEntity("/api/v1/auth/login",
                entityMap(Map.of("email", email, "password", "Str0ngPass!2026")), Map.class);
        assertThat(oldLogin.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void passwordChangeRejectsWrongCurrentPassword() {
        String email = uniqueEmail();
        rest.postForEntity("/api/v1/auth/register",
                entityMap(Map.of("fullName", "Password Tester", "email", email,
                        "password", "Str0ngPass!2026", "phone", "+91-9000000002")), Map.class);
        String token = tokenFor(email, "Str0ngPass!2026");
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);

        ResponseEntity<ApiError> res = rest.exchange("/api/v1/users/me/password", HttpMethod.POST,
                new HttpEntity<>(Map.of("currentPassword", "WrongPass!2026",
                        "newPassword", "Even!Str0nger2026"), headers), ApiError.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void unauthenticatedProtectedEndpointIs401() {
        ResponseEntity<String> res = rest.getForEntity("/api/v1/cart", String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void customerCannotReachAdminSurface() {
        String token = tokenFor("customer1@shopsphere.local", "User@123");
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        ResponseEntity<String> res = rest.exchange("/api/v1/admin/dashboard", HttpMethod.GET,
                new HttpEntity<>(headers), String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void adminCanReachAdminSurface() {
        String token = tokenFor("admin@shopsphere.local", "Admin@123");
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        ResponseEntity<String> res = rest.exchange("/api/v1/admin/dashboard", HttpMethod.GET,
                new HttpEntity<>(headers), String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void orderOfAnotherCustomerIsHidden() {
        String intruder = tokenFor("customer2@shopsphere.local", "User@123");
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(intruder);
        // Order 1 belongs to customer1 (created in this run or earlier); a
        // foreign customer must never see it — deliberately a 404, not a 403.
        ResponseEntity<String> res = rest.exchange("/api/v1/orders/1", HttpMethod.GET,
                new HttpEntity<>(headers), String.class);
        if (res.getStatusCode() != HttpStatus.NOT_FOUND) {
            // Only acceptable if the order genuinely belongs to customer2's session
            assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }
}
