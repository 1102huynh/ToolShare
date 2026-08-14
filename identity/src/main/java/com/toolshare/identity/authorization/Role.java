package com.toolshare.identity.authorization;

import java.util.EnumSet;
import java.util.Set;

public enum Role {
    RENTER(EnumSet.of(Permission.RENTER_ACCESS)),
    OWNER(EnumSet.of(Permission.OWNER_ACCESS)),
    ADMIN(EnumSet.of(Permission.ADMIN_ACCESS));

    private final Set<Permission> permissions;

    Role(Set<Permission> permissions) {
        this.permissions = EnumSet.copyOf(permissions);
    }

    public Set<Permission> getPermissions() {
        return EnumSet.copyOf(permissions);
    }
}
