package com.example.common.auth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PasswordsTest {

    @Test
    void hashesWithSaltAndVerifies() {
        String a = Passwords.hash("Secret123");
        String b = Passwords.hash("Secret123");
        assertNotEquals(a, b);
        assertTrue(a.startsWith("$2a$10$"));
        assertTrue(Passwords.matches("Secret123", a));
        assertFalse(Passwords.matches("secret123", a));
    }

    @Test
    void neverThrowsOnBadInput() {
        assertFalse(Passwords.matches(null, Passwords.hash("x")));
        assertFalse(Passwords.matches("x", null));
        assertFalse(Passwords.matches("x", "not-a-bcrypt-hash"));
    }
}
