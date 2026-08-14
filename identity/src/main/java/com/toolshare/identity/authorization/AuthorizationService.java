package com.toolshare.identity.authorization;

import com.toolshare.identity.domain.IdentityAccount;
import org.springframework.stereotype.Service;

@Service
public class AuthorizationService {

    public boolean hasPermission(IdentityAccount account, Permission permission) {
        if (account == null || permission == null) {
            return false;
        }
        return account.hasPermission(permission);
    }

    public boolean hasAnyPermission(IdentityAccount account, Permission... permissions) {
        if (account == null || permissions == null || permissions.length == 0) {
            return false;
        }
        for (Permission permission : permissions) {
            if (account.hasPermission(permission)) {
                return true;
            }
        }
        return false;
    }
}
