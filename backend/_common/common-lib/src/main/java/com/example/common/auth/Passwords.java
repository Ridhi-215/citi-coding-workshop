package com.example.common.auth;

import org.mindrot.jbcrypt.BCrypt;

/**
 * Password hashing with bcrypt.
 */
public final class Passwords {

    /** bcrypt work factor: about 100 ms per hash on a 1024 MB Lambda. */
    static final int COST = 10;

    /** Longest password accepted; bcrypt ignores bytes after 72. */
    public static final int MAX_LENGTH = 72;

    private Passwords() {
    }

    /**
     * Hashes a password with a fresh salt.
     *
     * @param plain password in plain text
     * @return bcrypt hash (includes salt and cost)
     */
    public static String hash(String plain) {
        return BCrypt.hashpw(plain, BCrypt.gensalt(COST));
    }

    /**
     * Checks a password against a stored hash.
     *
     * @param plain password in plain text
     * @param hash  stored bcrypt hash
     * @return true if they match; false for a null or malformed hash
     */
    public static boolean matches(String plain, String hash) {
        if (plain == null || hash == null || !hash.startsWith("$2")) {
            return false;
        }
        try {
            return BCrypt.checkpw(plain, hash);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
