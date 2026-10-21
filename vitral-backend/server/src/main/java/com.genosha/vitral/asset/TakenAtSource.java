package com.genosha.vitral.asset;

/** Where an asset's capture date came from, from most to least reliable. */
public enum TakenAtSource {
    /** Written by the camera into the file's metadata. */
    EXIF,
    /** The file's modification time, as reported by the uploading client. */
    FILE,
    /** The moment the server received the file; used when nothing better exists. */
    UPLOAD
}