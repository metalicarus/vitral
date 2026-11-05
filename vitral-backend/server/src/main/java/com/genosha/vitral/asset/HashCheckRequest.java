package com.genosha.vitral.asset;

import java.util.List;

/** Body of POST /api/v1/assets/check: SHA-256 hashes of files the client is about to upload. */
public record HashCheckRequest(List<String> hashes) {
}