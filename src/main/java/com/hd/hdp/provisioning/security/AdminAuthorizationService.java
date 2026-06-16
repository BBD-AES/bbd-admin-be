package com.hd.hdp.provisioning.security;

import com.hd.hdp.provisioning.config.ProvisioningProperties;
import com.hd.hdp.provisioning.error.ProvisioningException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

@Component
public class AdminAuthorizationService {

    private final ProvisioningProperties properties;

    public AdminAuthorizationService(ProvisioningProperties properties) {
        this.properties = properties;
    }

    public void requireAdmin(Authentication authentication) {
        if (!properties.getSecurity().isEnforceAdminRole()) {
            return;
        }

        Set<String> roles = extractRoles(authentication);
        String requiredRole = properties.getSecurity().getRequiredAdminRole();

        if (!roles.contains(requiredRole)) {
            throw new ProvisioningException(
                    HttpStatus.FORBIDDEN,
                    "ADMIN_ROLE_REQUIRED",
                    "Keycloak role이 부족합니다. requiredRole=" + requiredRole
            );
        }
    }

    public boolean isAdmin(Authentication authentication) {
        if (authentication == null
                || authentication instanceof AnonymousAuthenticationToken
                || !authentication.isAuthenticated()) {
            return false;
        }
        if (!properties.getSecurity().isEnforceAdminRole()) {
            return true;
        }
        return extractRoles(authentication).contains(properties.getSecurity().getRequiredAdminRole());
    }

    public Set<String> extractRoles(Authentication authentication) {
        Set<String> roles = new LinkedHashSet<>();
        if (authentication == null
                || authentication instanceof AnonymousAuthenticationToken
                || !authentication.isAuthenticated()) {
            return roles;
        }

        authentication.getAuthorities().forEach(authority -> {
            roles.add(authority.getAuthority());
            if (authority.getAuthority().startsWith("ROLE_")) {
                roles.add(authority.getAuthority().substring("ROLE_".length()));
            }
        });

        Object principal = authentication.getPrincipal();
        if (principal instanceof OidcUser oidcUser) {
            roles.addAll(extractClaimRoles(oidcUser.getClaims()));
        } else if (principal instanceof Jwt jwt) {
            roles.addAll(extractClaimRoles(jwt.getClaims()));
        }

        return roles;
    }

    private Set<String> extractClaimRoles(Map<String, Object> claims) {
        Set<String> roles = new LinkedHashSet<>();
        Object realmAccess = claims.get("realm_access");
        if (realmAccess instanceof Map<?, ?> realmMap) {
            addRoles(roles, realmMap.get("roles"));
        }

        Object resourceAccess = claims.get("resource_access");
        if (resourceAccess instanceof Map<?, ?> resourceMap) {
            resourceMap.values().forEach(clientAccess -> {
                if (clientAccess instanceof Map<?, ?> clientMap) {
                    addRoles(roles, clientMap.get("roles"));
                }
            });
        }
        return roles;
    }

    private void addRoles(Set<String> roles, Object value) {
        if (value instanceof Iterable<?> iterable) {
            iterable.forEach(role -> {
                if (role != null) {
                    roles.add(role.toString());
                }
            });
        }
    }
}
