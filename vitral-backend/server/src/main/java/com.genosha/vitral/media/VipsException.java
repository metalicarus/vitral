package com.genosha.vitral.media;

/** libvips ran but could not process the file, for example because it is corrupted. */
public class VipsException extends Exception {

    public VipsException(String message) {
        super(message);
    }
}