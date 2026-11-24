package com.example;

import com.example.common.auth.Passwords;
import com.example.common.auth.Principal;
import com.example.common.auth.Tokens;
import com.example.common.db.Db;
import com.example.common.db.Sql;
import com.example.common.http.ApiException;
import com.example.common.http.Request;
import com.example.common.http.Responses;
import com.example.common.security.Permission;
import com.example.common.security.Permissions;
import com.example.common.security.Role;
import com.example.common.validation.Validator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Registration, login, token refresh and the current user.
 */
final class AuthApi {

    /** Columns returned for a user; never includes the password hash. */
    static final String USER_COLUMNS = "id, email, full_name, role, is_active, created_at, updated_at";

    /** Compared against when the email is unknown, so both cases take the same time. */
    private static final String DUMMY_HASH = Passwords.hash("timing-equaliser");

    private final Supplier<Tokens> tokens;

    AuthApi(Supplier<Tokens> tokens) {
        this.tokens = tokens;
    }

    /**
     * {@code POST /register}: self sign-up. New accounts are always viewers; any {@code role}
     * in the body is ignored (only an admin can change roles). 409 if the email is taken.
     */
    Map<String, Object> register(Request req) throws Exception {
        Validator v = new Validator(req.json());
        String email = v.requiredEmail("email");
        String password = UsersApi.password(v, "password", true);
        String name = v.requiredString("full_name", 120);
        v.throwIfInvalid();

        UsersApi.ensureEmailFree(email);
        Map<String, Object> user = Sql.queryOne(Db.get(),
            "INSERT INTO users (email, password_hash, full_name, role) VALUES (?, ?, ?, 'viewer') RETURNING "
                + USER_COLUMNS,
            email, Passwords.hash(password), name).orElseThrow();
        return Responses.created(session(user));
    }

    /** {@code POST /login}: email + password to access and refresh tokens. */
    Map<String, Object> login(Request req) throws Exception {
        Validator v = new Validator(req.json());
        String email = v.requiredString("email", 254);
        String password = v.requiredString("password", Passwords.MAX_LENGTH);
        v.throwIfInvalid();

        Optional<Map<String, Object>> row = Sql.queryOne(Db.get(),
            "SELECT " + USER_COLUMNS + ", password_hash FROM users WHERE email = ?", email.toLowerCase());
        String hash = row.map(r -> (String) r.get("password_hash")).orElse(DUMMY_HASH);
        boolean ok = Passwords.matches(password, hash) && row.isPresent();
        if (!ok || !Boolean.TRUE.equals(row.get().get("is_active"))) {
            throw ApiException.unauthorized("INVALID_CREDENTIALS", "Email or password is incorrect");
        }
        Map<String, Object> user = row.get();
        user.remove("password_hash");
        return Responses.ok(session(user));
    }

    /** {@code POST /refresh}: exchanges a refresh token for new tokens, re-reading the role. */
    Map<String, Object> refresh(Request req) throws Exception {
        Validator v = new Validator(req.json());
        String token = v.requiredString("refresh_token", 4096);
        v.throwIfInvalid();

        Principal p = tokens.get().verify(token, Tokens.REFRESH);
        Map<String, Object> user = Sql.queryOne(Db.get(),
                "SELECT " + USER_COLUMNS + " FROM users WHERE id = ? AND is_active", p.userId())
            .orElseThrow(() -> ApiException.unauthorized("INVALID_TOKEN", "Account is no longer active"));
        return Responses.ok(session(user));
    }

    /** {@code GET /me}: the signed-in user and what their role may do. */
    Map<String, Object> me(Request req) throws Exception {
        Map<String, Object> user = Sql.queryOne(Db.get(),
                "SELECT " + USER_COLUMNS + " FROM users WHERE id = ?", req.principal().userId())
            .orElseThrow(() -> ApiException.unauthorized("INVALID_TOKEN", "Account no longer exists"));
        return Responses.ok(withPermissions(user));
    }

    private Map<String, Object> session(Map<String, Object> user) {
        Principal p = new Principal(((Number) user.get("id")).longValue(), (String) user.get("email"),
            (String) user.get("full_name"), Role.from((String) user.get("role")));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("access_token", tokens.get().issue(p, Tokens.ACCESS));
        body.put("refresh_token", tokens.get().issue(p, Tokens.REFRESH));
        body.put("token_type", "Bearer");
        body.put("expires_in", Tokens.ACCESS_TTL.toSeconds());
        body.put("user", withPermissions(user));
        return body;
    }

    static Map<String, Object> withPermissions(Map<String, Object> user) {
        Role role = Role.from((String) user.get("role"));
        user.put("permissions", Permissions.of(role).stream().map(Permission::name).sorted().toList());
        return user;
    }
}
