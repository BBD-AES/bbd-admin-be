package com.hd.hdp.provisioning.web;

import com.hd.hdp.provisioning.security.AdminAuthorizationService;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

@RestController
public class SessionController {

    private static final String LOGIN_URL = "/oauth2/authorization/keycloak";
    private static final String LOGOUT_URL = "/logout";

    private final AdminAuthorizationService adminAuthorizationService;

    public SessionController(AdminAuthorizationService adminAuthorizationService) {
        this.adminAuthorizationService = adminAuthorizationService;
    }

    @GetMapping("/api/session/me")
    SessionResponse me(Authentication authentication) {
        if (authentication == null
                || authentication instanceof AnonymousAuthenticationToken
                || !authentication.isAuthenticated()) {
            return SessionResponse.anonymous();
        }

        PrincipalView principal = principal(authentication);
        Set<String> roles = adminAuthorizationService.extractRoles(authentication);

        return new SessionResponse(
                true,
                adminAuthorizationService.isAdmin(authentication),
                principal.subject(),
                principal.username(),
                principal.name(),
                principal.email(),
                roles,
                LOGIN_URL,
                LOGOUT_URL
        );
    }

    @GetMapping("/api/session/login-url")
    LoginUrlResponse loginUrl() {
        return new LoginUrlResponse(LOGIN_URL);
    }

    private PrincipalView principal(Authentication authentication) {
        Object principal = authentication.getPrincipal();
        if (principal instanceof OidcUser oidcUser) {
            return new PrincipalView(
                    oidcUser.getSubject(),
                    oidcUser.getPreferredUsername(),
                    oidcUser.getFullName(),
                    oidcUser.getEmail()
            );
        }
        if (principal instanceof Jwt jwt) {
            return new PrincipalView(
                    jwt.getSubject(),
                    jwt.getClaimAsString("preferred_username"),
                    jwt.getClaimAsString("name"),
                    jwt.getClaimAsString("email")
            );
        }
        return new PrincipalView(null, authentication.getName(), authentication.getName(), null);
    }

    record LoginUrlResponse(String loginUrl) {
    }

    record PrincipalView(
            String subject,
            String username,
            String name,
            String email
    ) {
    }

    record SessionResponse(
            boolean authenticated,
            boolean admin,
            String subject,
            String username,
            String name,
            String email,
            Set<String> roles,
            String loginUrl,
            String logoutUrl
    ) {
        static SessionResponse anonymous() {
            return new SessionResponse(
                    false,
                    false,
                    null,
                    null,
                    null,
                    null,
                    Set.of(),
                    LOGIN_URL,
                    LOGOUT_URL
            );
        }
    }
}
