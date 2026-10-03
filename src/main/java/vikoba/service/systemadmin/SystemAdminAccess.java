package vikoba.service.systemadmin;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vikoba.service.auth.repository.UserRoleRepository;

@Service
@RequiredArgsConstructor
public class SystemAdminAccess {
    private final UserRoleRepository roles;
    @Transactional(readOnly = true)
    public boolean isSuperAdmin() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName())
                && roles.findByUserPhoneWithPermissions(auth.getName()).stream()
                .anyMatch(grant -> "SUPER_ADMIN".equals(grant.getRole().getName()));
    }
    public void requireSuperAdmin() {
        if (!isSuperAdmin()) throw new AccessDeniedException("System administrator access is required.");
    }
}
