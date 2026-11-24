package com.example.common.security;

import com.example.common.auth.Principal;
import com.example.common.http.ApiException;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * The single source of truth for what each role may do.
 *
 * <p>The frontend mirrors this table (via the role in the token) only to hide buttons;
 * enforcement always happens here.
 */
public final class Permissions {

    private static final Map<Role, Set<Permission>> TABLE = new EnumMap<>(Role.class);

    static {
        TABLE.put(Role.ADMIN, EnumSet.allOf(Permission.class));
        TABLE.put(Role.MANAGER, EnumSet.of(Permission.READ, Permission.CREATE, Permission.UPDATE, Permission.DELETE));
        TABLE.put(Role.CONTRIBUTOR, EnumSet.of(Permission.READ, Permission.CREATE, Permission.UPDATE));
        TABLE.put(Role.VIEWER, EnumSet.of(Permission.READ));
    }

    private Permissions() {
    }

    /**
     * Checks whether a role has a permission.
     *
     * @param role       role to check
     * @param permission required permission
     * @return true if allowed
     */
    public static boolean allows(Role role, Permission permission) {
        return role != null && TABLE.get(role).contains(permission);
    }

    /**
     * Returns every permission of a role (sent to the frontend after login).
     *
     * @param role role
     * @return immutable set of permissions
     */
    public static Set<Permission> of(Role role) {
        return Set.copyOf(TABLE.get(role));
    }

    /**
     * Throws unless the caller has the permission.
     *
     * @param principal  authenticated caller
     * @param permission required permission
     * @throws ApiException 403 if the role does not allow the action
     */
    public static void require(Principal principal, Permission permission) {
        if (principal == null || !allows(principal.role(), permission)) {
            throw ApiException.forbidden("Your role does not allow this action");
        }
    }
}
