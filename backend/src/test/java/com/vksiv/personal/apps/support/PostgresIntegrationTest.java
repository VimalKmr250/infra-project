package com.vksiv.personal.apps.support;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import com.vksiv.personal.apps.note.NoteRepository;
import com.vksiv.personal.apps.user.UserRepository;

/**
 * Base class for tests that need a real PostgreSQL.
 *
 * The container is a singleton started once per JVM rather than one per test
 * class: starting Postgres costs a few seconds, and there is no isolation
 * benefit to repeating it when each test truncates what it uses.
 *
 * Version 17 matches what Supabase runs in production and what the compose
 * files use locally and on the Pi, so migrations are exercised against the
 * same major version everywhere.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
public abstract class PostgresIntegrationTest {

    // Testcontainers 2.x dropped the self-type generic, so this is no longer PostgreSQLContainer<?>.
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    protected TestRestTemplate rest;

    @Autowired
    private NoteRepository notes;

    @Autowired
    private UserRepository users;

    @BeforeEach
    void resetDatabase() {
        notes.deleteAll();
        users.deleteAll();
    }
}
