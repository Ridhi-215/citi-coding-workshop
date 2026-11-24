package com.example.common.security;

/**
 * Actions a route can require.
 */
public enum Permission {
    /** Read any project data. */
    READ,
    /** Create records. */
    CREATE,
    /** Update records. */
    UPDATE,
    /** Delete records. */
    DELETE,
    /** List, change roles of, deactivate and delete users. */
    MANAGE_USERS
}
