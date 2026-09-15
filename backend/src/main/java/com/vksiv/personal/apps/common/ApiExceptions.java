package com.vksiv.personal.apps.common;

/** Domain-level failures that map onto specific HTTP statuses. */
public final class ApiExceptions {

    private ApiExceptions() {
    }

    /** 409 - the email is already registered. */
    public static class EmailAlreadyUsedException extends RuntimeException {
        public EmailAlreadyUsedException(String email) {
            super("Email already registered: " + email);
        }
    }

    /** 401 - bad email/password, or an unusable refresh token. */
    public static class InvalidCredentialsException extends RuntimeException {
        public InvalidCredentialsException(String message) {
            super(message);
        }
    }

    /** 404 - the resource does not exist, or does not belong to the caller. */
    public static class NotFoundException extends RuntimeException {
        public NotFoundException(String message) {
            super(message);
        }
    }
}
