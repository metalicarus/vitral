package com.genosha.vitral.client.api;

import java.io.IOException;

/**
 * The server answered with an error. {@code code} is the server's stable, machine-readable
 * error code (such as {@code invalid_credentials}); the message is the server's English hint.
 */
public class ApiException extends IOException {

    private final int status;
    private final String code;

    public ApiException(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public int status() {
        return status;
    }

    public String code() {
        return code;
    }

    /** The session token is unknown or expired: the user has to log in again. */
    public boolean isUnauthorized() {
        return status == 401;
    }
}
