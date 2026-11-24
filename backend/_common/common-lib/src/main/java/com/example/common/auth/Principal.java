package com.example.common.auth;

import com.example.common.security.Role;

/**
 * The authenticated caller, taken from a verified access token.
 *
 * @param userId user id ({@code sub} claim)
 * @param email  user email
 * @param name   display name
 * @param role   role used for permission checks
 */
public record Principal(long userId, String email, String name, Role role) {
}
