package com.genosha.vitral.common;

import jakarta.ws.rs.core.Response;

/**
 * An error meant for API clients. The {@code code} is stable and machine-readable,
 * so clients can translate it; the message is a human-readable hint in English.
 */
public class ApiException extends RuntimeException {

    private final Response.Status status;
    private final String code;

    public ApiException(Response.Status status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public Response.Status status() {
        return status;
    }

    public String code() {
        return code;
    }

    public static ApiException badRequest(String code, String message) {
        return new ApiException(Response.Status.BAD_REQUEST, code, message);
    }

    public static ApiException unauthorized(String code, String message) {
        return new ApiException(Response.Status.UNAUTHORIZED, code, message);
    }

    public static ApiException forbidden(String code, String message) {
        return new ApiException(Response.Status.FORBIDDEN, code, message);
    }

    public static ApiException notFound(String code, String message) {
        return new ApiException(Response.Status.NOT_FOUND, code, message);
    }

    public static ApiException conflict(String code, String message) {
        return new ApiException(Response.Status.CONFLICT, code, message);
    }

    public static ApiException unsupportedMediaType(String code, String message) {
        return new ApiException(Response.Status.UNSUPPORTED_MEDIA_TYPE, code, message);
    }

    public static ApiException tooManyRequests(String code, String message) {
        return new ApiException(Response.Status.TOO_MANY_REQUESTS, code, message);
    }
}