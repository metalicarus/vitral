package com.genosha.vitral.asset;

import java.util.List;
import java.util.UUID;

/**
 * Which of the requested hashes the user already has on the server, and which are missing.
 * Both lists keep the order of the request and use lowercase hashes.
 */
public record HashCheckResult(List<Existing> existing, List<String> missing) {

    public record Existing(String sha256, UUID id) {
    }
}