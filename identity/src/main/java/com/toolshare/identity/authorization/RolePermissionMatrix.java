package com.toolshare.identity.authorization;

import java.util.EnumSet;
import java.util.Set;

public final class RolePermissionMatrix {

    private RolePermissionMatrix() {
    }

    public static Set<Permission> permissionsFor(Role role) {
        if (role == null) {
            return EnumSet.noneOf(Permission.class);
        }
        return role.getPermissions();
    }

    public static Set<Permission> permissionsFor(Set<Role> roles) {
        if (roles == null || roles.isEmpty()) {
            return EnumSet.noneOf(Permission.class);
        }

        EnumSet<Permission> permissions = EnumSet.noneOf(Permission.class);
        for (Role role : roles) {
            permissions.addAll(role.getPermissions());
        }
        return permissions;
    }

    public static boolean hasPermission(Set<Role> roles, Permission permission) {
        if (permission == null) {
            return false;
        }
        return permissionsFor(roles).contains(permission);
    }
}
