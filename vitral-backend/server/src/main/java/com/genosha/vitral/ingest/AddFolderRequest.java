package com.genosha.vitral.ingest;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of POST /api/v1/admin/folders.
 *
 * @param path       an absolute path on the server, such as /photos/maria
 * @param ownerEmail the user whose library the folder's photos go to
 */
public record AddFolderRequest(
        @NotBlank @Size(max = 4096) String path,
        @NotBlank @Email @Size(max = 320) String ownerEmail) {
}
