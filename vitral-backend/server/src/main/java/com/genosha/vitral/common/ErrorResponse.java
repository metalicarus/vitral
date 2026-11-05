package com.genosha.vitral.common;

/** JSON body returned for every {@link ApiException}. */
public record ErrorResponse(String error, String message) {
}
