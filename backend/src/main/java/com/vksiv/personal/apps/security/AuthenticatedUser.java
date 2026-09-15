package com.vksiv.personal.apps.security;

import java.util.UUID;

/** The principal placed on the SecurityContext for an authenticated request. */
public record AuthenticatedUser(UUID id, String email) {
}
