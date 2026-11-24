package com.example.common.security;

import java.util.Locale;

/**
 * User roles, stored in {@code users.role} as lower-case text.
 */
public enum Role {
    /** Full access, including user and role management. */
    ADMIN,
    /** Everything except user and role management. */
    MANAGER,
    /** Can read, create and update; cannot delete. */
    CONTRIBUTOR,
    /** Read-only. */
    VIEWER;

    /**
     * Returns the value stored in the database and in tokens.
     *
     * @return lower-case role name
     */
    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * Parses a stored role value.
     *
     * @param value role name, any case
     * @return matching role
     * @throws IllegalArgumentException if the value is not a role
     */
    public static Role from(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Role is required");
        }
        return Role.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
