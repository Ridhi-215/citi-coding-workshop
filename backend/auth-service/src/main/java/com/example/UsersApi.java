package com.example;

import com.example.common.auth.Passwords;
import com.example.common.db.Db;
import com.example.common.db.Sql;
import com.example.common.http.ApiException;
import com.example.common.http.Request;
import com.example.common.http.Responses;
import com.example.common.query.Page;
import com.example.common.query.QueryBuilder;
import com.example.common.validation.Validator;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User management for admins: {@code /users} list, read, create, update, delete.
 */
final class UsersApi {

    static final Set<String> ROLES = Set.of("admin", "manager", "contributor", "viewer");
    private static final Set<String> SORTABLE = Set.of("email", "full_name", "role", "created_at");
    private static final int MIN_PASSWORD = 8;

    /** {@code GET /users?search=&role=&active=&sort=&order=&limit=&offset=}. */
    Map<String, Object> list(Request req) throws Exception {
        QueryBuilder q = new QueryBuilder("SELECT " + AuthApi.USER_COLUMNS + " FROM users")
            .search(req.query("search"), "email", "full_name")
            .eqEnum("role", req.query("role"), ROLES, "role")
            .whereIf(req.queryBool("active"), "is_active = ?", req.queryBool("active"))
            .orderBy(req, SORTABLE, "email")
            .page(req);
        return Responses.ok(Page.of(Sql.query(Db.get(), q.sql(), q.params()), q));
    }

    /** {@code GET /users/{id}}. */
    Map<String, Object> get(Request req) throws Exception {
        return Responses.ok(find(req.pathId("id")));
    }

    /** {@code POST /users}: admin creates an account with any role. */
    Map<String, Object> create(Request req) throws Exception {
        Validator v = new Validator(req.json());
        String email = v.requiredEmail("email");
        String password = password(v, "password", true);
        String name = v.requiredString("full_name", 120);
        String role = v.optionalEnum("role", ROLES, "viewer");
        v.throwIfInvalid();
        if (Sql.queryOne(Db.get(), "SELECT 1 FROM users WHERE email = ?", email).isPresent()) {
            throw ApiException.validation(Map.of("email", "An account with this email already exists"));
        }
        Map<String, Object> user = Sql.queryOne(Db.get(),
            "INSERT INTO users (email, password_hash, full_name, role) VALUES (?, ?, ?, ?) RETURNING "
                + AuthApi.USER_COLUMNS,
            email, Passwords.hash(password), name, role).orElseThrow();
        return Responses.created(user);
    }

    /** {@code PUT /users/{id}}: partial update of name, role, active flag and password. */
    Map<String, Object> update(Request req) throws Exception {
        long id = req.pathId("id");
        Map<String, Object> body = req.json();
        Validator v = new Validator(body);
        String name = v.optionalString("full_name", 120);
        String role = body.containsKey("role") ? v.requiredEnum("role", ROLES) : null;
        Boolean active = null;
        if (body.containsKey("is_active")) {
            v.check(body.get("is_active") instanceof Boolean, "is_active", "must be true or false");
            active = body.get("is_active") instanceof Boolean b ? b : null;
        }
        String password = password(v, "password", false);
        boolean self = id == req.principal().userId();
        v.check(!(self && role != null && !"admin".equals(role)), "role", "You cannot remove your own admin role");
        v.check(!(self && Boolean.FALSE.equals(active)), "is_active", "You cannot deactivate your own account");
        v.throwIfInvalid();
        Map<String, Object> current = find(id);
        boolean losesAdmin = (role != null && !"admin".equals(role)) || Boolean.FALSE.equals(active);
        if (losesAdmin && isLastActiveAdmin(current)) {
            throw ApiException.badRequest("At least one active admin must remain");
        }

        List<String> sets = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        if (name != null) {
            sets.add("full_name = ?");
            params.add(name);
        }
        if (role != null) {
            sets.add("role = ?");
            params.add(role);
        }
        if (active != null) {
            sets.add("is_active = ?");
            params.add(active);
        }
        if (password != null) {
            sets.add("password_hash = ?");
            params.add(Passwords.hash(password));
        }
        sets.add("updated_at = now()");
        params.add(id);
        Sql.update(Db.get(), "UPDATE users SET " + String.join(", ", sets) + " WHERE id = ?", params.toArray());
        return Responses.ok(find(id));
    }

    /** {@code DELETE /users/{id}}: admins cannot delete themselves. */
    Map<String, Object> delete(Request req) throws Exception {
        long id = req.pathId("id");
        if (id == req.principal().userId()) {
            throw ApiException.badRequest("You cannot delete your own account");
        }
        if (isLastActiveAdmin(find(id))) {
            throw ApiException.badRequest("At least one active admin must remain");
        }
        Sql.update(Db.get(), "DELETE FROM users WHERE id = ?", id);
        return Responses.noContent();
    }

    /** True when the user is the only active admin (defence in depth next to the self checks). */
    private static boolean isLastActiveAdmin(Map<String, Object> user) throws Exception {
        return "admin".equals(user.get("role")) && Boolean.TRUE.equals(user.get("is_active"))
            && Sql.scalar(Db.get(), "SELECT count(*) FROM users WHERE role = 'admin' AND is_active") <= 1;
    }

    private static Map<String, Object> find(long id) throws Exception {
        return Sql.queryOne(Db.get(), "SELECT " + AuthApi.USER_COLUMNS + " FROM users WHERE id = ?", id)
            .orElseThrow(() -> ApiException.notFound("User", id));
    }

    /**
     * Validates a password field: 8-72 characters with at least one letter and one digit.
     *
     * @return the password, or null when optional and absent or invalid
     */
    static String password(Validator v, String field, boolean required) {
        String pw = required ? v.requiredString(field, Passwords.MAX_LENGTH) : v.optionalString(field, Passwords.MAX_LENGTH);
        if (pw != null) {
            v.check(pw.length() >= MIN_PASSWORD, field, "must be at least " + MIN_PASSWORD + " characters");
            v.check(pw.chars().anyMatch(Character::isLetter) && pw.chars().anyMatch(Character::isDigit),
                field, "must contain a letter and a digit");
        }
        return pw;
    }
}
