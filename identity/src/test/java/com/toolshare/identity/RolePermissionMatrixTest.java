package com.toolshare.identity;

import com.toolshare.identity.authorization.Permission;
import com.toolshare.identity.authorization.Role;
import com.toolshare.identity.authorization.RolePermissionMatrix;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RolePermissionMatrixTest {

    @Test
    void renter_has_only_renter_permissions() {
        Set<Permission> permissions = RolePermissionMatrix.permissionsFor(Role.RENTER);

        assertEquals(EnumSet.of(Permission.RENTER_ACCESS), permissions);
    }

    @Test
    void owner_has_only_owner_permissions() {
        Set<Permission> permissions = RolePermissionMatrix.permissionsFor(Role.OWNER);

        assertEquals(EnumSet.of(Permission.OWNER_ACCESS), permissions);
    }

    @Test
    void admin_has_admin_permissions_only() {
        Set<Permission> permissions = RolePermissionMatrix.permissionsFor(Role.ADMIN);

        assertEquals(EnumSet.of(Permission.ADMIN_ACCESS), permissions);
    }

    @Test
    void renter_and_owner_union_has_combined_permissions() {
        Set<Permission> permissions = RolePermissionMatrix.permissionsFor(Set.of(Role.RENTER, Role.OWNER));

        assertEquals(EnumSet.of(Permission.RENTER_ACCESS, Permission.OWNER_ACCESS), permissions);
    }

    @Test
    void multiple_roles_are_merged_deterministically() {
        Set<Permission> permissions = RolePermissionMatrix.permissionsFor(Set.of(Role.ADMIN, Role.RENTER));

        assertTrue(permissions.contains(Permission.ADMIN_ACCESS));
        assertTrue(permissions.contains(Permission.RENTER_ACCESS));
        assertFalse(permissions.contains(Permission.OWNER_ACCESS));
    }

    @Test
    void permissions_are_denied_when_required_permission_is_missing() {
        assertFalse(RolePermissionMatrix.hasPermission(Set.of(Role.RENTER), Permission.OWNER_ACCESS));
        assertFalse(RolePermissionMatrix.hasPermission(Set.of(Role.OWNER), Permission.RENTER_ACCESS));
        assertFalse(RolePermissionMatrix.hasPermission(Set.of(), Permission.RENTER_ACCESS));
    }

    @Test
    void permission_checks_accept_union_of_roles() {
        assertTrue(RolePermissionMatrix.hasPermission(Set.of(Role.RENTER, Role.OWNER), Permission.OWNER_ACCESS));
        assertTrue(RolePermissionMatrix.hasPermission(Set.of(Role.RENTER, Role.OWNER), Permission.RENTER_ACCESS));
    }
}
