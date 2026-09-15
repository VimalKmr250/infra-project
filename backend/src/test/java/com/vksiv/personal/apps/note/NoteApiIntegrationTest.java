package com.vksiv.personal.apps.note;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.vksiv.personal.apps.support.PostgresIntegrationTest;

/**
 * Exercises the full path the scaffold exists to prove: HTTP in, Spring Security,
 * JPA, a Liquibase-managed schema, and a real PostgreSQL container.
 */
class NoteApiIntegrationTest extends PostgresIntegrationTest {

    private static final ParameterizedTypeReference<Map<String, Object>> JSON_OBJECT =
            new ParameterizedTypeReference<>() {
            };
    private static final ParameterizedTypeReference<List<Map<String, Object>>> JSON_ARRAY =
            new ParameterizedTypeReference<>() {
            };

    @Test
    @DisplayName("registers a user, creates a note, and reads it back")
    void fullHappyPath() {
        String token = registerAndGetAccessToken("alice@example.test", "Alice");

        ResponseEntity<Map<String, Object>> created = rest.exchange(
                "/api/notes", HttpMethod.POST,
                new HttpEntity<>(Map.of("title", "First", "body", "Hello"), bearer(token)),
                JSON_OBJECT);

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody()).containsEntry("title", "First");
        assertThat(created.getBody()).containsKey("id");

        ResponseEntity<List<Map<String, Object>>> listed = rest.exchange(
                "/api/notes", HttpMethod.GET, new HttpEntity<>(bearer(token)), JSON_ARRAY);

        assertThat(listed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listed.getBody()).hasSize(1);
        assertThat(listed.getBody().get(0)).containsEntry("body", "Hello");
    }

    @Test
    @DisplayName("rejects an unauthenticated request with 401")
    void requiresAuthentication() {
        ResponseEntity<String> response = rest.getForEntity("/api/notes", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("one user cannot see or fetch another user's notes")
    void notesAreScopedToTheirOwner() {
        String aliceToken = registerAndGetAccessToken("alice2@example.test", "Alice");
        String bobToken = registerAndGetAccessToken("bob@example.test", "Bob");

        ResponseEntity<Map<String, Object>> aliceNote = rest.exchange(
                "/api/notes", HttpMethod.POST,
                new HttpEntity<>(Map.of("title", "Private", "body", "Secret"), bearer(aliceToken)),
                JSON_OBJECT);
        String noteId = String.valueOf(aliceNote.getBody().get("id"));

        ResponseEntity<List<Map<String, Object>>> bobList = rest.exchange(
                "/api/notes", HttpMethod.GET, new HttpEntity<>(bearer(bobToken)), JSON_ARRAY);
        assertThat(bobList.getBody()).isEmpty();

        ResponseEntity<String> bobDirectFetch = rest.exchange(
                "/api/notes/" + noteId, HttpMethod.GET, new HttpEntity<>(bearer(bobToken)), String.class);
        assertThat(bobDirectFetch.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("refuses to register the same email twice")
    void rejectsDuplicateEmail() {
        registerAndGetAccessToken("dupe@example.test", "First");

        ResponseEntity<String> second = rest.postForEntity("/api/auth/register",
                Map.of("email", "dupe@example.test", "password", "correct-horse-battery",
                        "displayName", "Second"),
                String.class);

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("rejects a short password with a field-level validation error")
    void rejectsWeakPassword() {
        ResponseEntity<String> response = rest.postForEntity("/api/auth/register",
                Map.of("email", "weak@example.test", "password", "short", "displayName", "Weak"),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("password");
    }

    @Test
    @DisplayName("an unknown API route returns 404, not 500")
    void unknownApiRouteIsNotFound() {
        String token = registerAndGetAccessToken("notfound@example.test", "NF");

        ResponseEntity<String> response = rest.exchange(
                "/api/does-not-exist", HttpMethod.GET, new HttpEntity<>(bearer(token)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private String registerAndGetAccessToken(String email, String displayName) {
        ResponseEntity<Map<String, Object>> response = rest.exchange(
                "/api/auth/register", HttpMethod.POST,
                new HttpEntity<>(Map.of(
                        "email", email,
                        "password", "correct-horse-battery",
                        "displayName", displayName)),
                JSON_OBJECT);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return String.valueOf(response.getBody().get("accessToken"));
    }

    private static HttpHeaders bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }
}
