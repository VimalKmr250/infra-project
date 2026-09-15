package com.vksiv.personal.apps.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Convenience accessor for the current principal. */
public final class SecurityPrincipal {

    private SecurityPrincipal() {
    }

    public static AuthenticatedUser current() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            throw new IllegalStateException("No authenticated user on the security context");
        }
        return user;
    }
}
