package com.hd.hdp.provisioning.security;

import com.nimbusds.jwt.JWTParser;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.text.ParseException;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

@Component
public class KeycloakRoleOidcUserService implements OAuth2UserService<OidcUserRequest, OidcUser> {

    private static final String ROLE_PREFIX = "ROLE_";

    private final OidcUserService delegate = new OidcUserService();

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        OidcUser oidcUser = delegate.loadUser(userRequest);
        Set<GrantedAuthority> authorities = new LinkedHashSet<>(oidcUser.getAuthorities());

        extractRoles(oidcUser.getClaims()).forEach(role -> addAuthority(authorities, role));
        extractRoles(accessTokenClaims(userRequest)).forEach(role -> addAuthority(authorities, role));

        String nameAttributeKey = userRequest.getClientRegistration()
                .getProviderDetails()
                .getUserInfoEndpoint()
                .getUserNameAttributeName();
        if (!StringUtils.hasText(nameAttributeKey)) {
            nameAttributeKey = IdTokenClaimNames.SUB;
        }

        OidcUserInfo userInfo = oidcUser.getUserInfo();
        if (userInfo == null) {
            return new DefaultOidcUser(authorities, oidcUser.getIdToken(), nameAttributeKey);
        }
        return new DefaultOidcUser(authorities, oidcUser.getIdToken(), userInfo, nameAttributeKey);
    }

    private Map<String, Object> accessTokenClaims(OidcUserRequest userRequest) {
        try {
            return JWTParser.parse(userRequest.getAccessToken().getTokenValue())
                    .getJWTClaimsSet()
                    .getClaims();
        } catch (ParseException exception) {
            return Map.of();
        }
    }

    private Set<String> extractRoles(Map<String, Object> claims) {
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

    private void addAuthority(Set<GrantedAuthority> authorities, String role) {
        authorities.add(new SimpleGrantedAuthority(role));
        if (!role.startsWith(ROLE_PREFIX)) {
            authorities.add(new SimpleGrantedAuthority(ROLE_PREFIX + role));
        }
    }
}
