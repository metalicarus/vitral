package dev.vitral.user;

public enum Role {
    /** Manages the server and its users. The first account is always an admin. */
    ADMIN,
    /** Regular member with their own library. */
    USER
}
