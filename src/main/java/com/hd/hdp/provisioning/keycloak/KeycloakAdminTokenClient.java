package com.hd.hdp.provisioning.keycloak;

import com.hd.hdp.provisioning.config.ProvisioningProperties;
import com.hd.hdp.provisioning.error.ProvisioningException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Instant;

@Component
public class KeycloakAdminTokenClient {

    private final RestClient keycloakRestClient;
    private final ProvisioningProperties properties;
    private volatile CachedToken cachedToken;

    public KeycloakAdminTokenClient(
            @Qualifier("keycloakRestClient") RestClient keycloakRestClient,
            ProvisioningProperties properties
    ) {
        this.keycloakRestClient = keycloakRestClient;
        this.properties = properties;
    }

    public String getAccessToken() {
        CachedToken current = cachedToken;
        if (current != null && current.isUsable()) {
            return current.accessToken();
        }

        synchronized (this) {
            current = cachedToken;
            if (current != null && current.isUsable()) {
                return current.accessToken();
            }

            cachedToken = requestToken();
            return cachedToken.accessToken();
        }
    }

    private CachedToken requestToken() {
        ProvisioningProperties.Keycloak keycloak = properties.getKeycloak();
        if (!StringUtils.hasText(keycloak.getAdminClientSecret())) {
            throw new ProvisioningException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "KEYCLOAK_ADMIN_CLIENT_SECRET_REQUIRED",
                    "KEYCLOAK_ADMIN_CLIENT_SECRET 값이 필요합니다."
            );
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", keycloak.getAdminClientId());
        form.add("client_secret", keycloak.getAdminClientSecret());

        try {
            KeycloakModels.TokenResponse response = keycloakRestClient
                    .post()
                    .uri("/realms/{realm}/protocol/openid-connect/token", keycloak.getRealm())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(KeycloakModels.TokenResponse.class);

            if (response == null || !StringUtils.hasText(response.accessToken())) {
                throw new ProvisioningException(
                        HttpStatus.BAD_GATEWAY,
                        "KEYCLOAK_TOKEN_EMPTY",
                        "Keycloak admin access token 응답이 비어 있습니다."
                );
            }

            long skew = Math.max(properties.getKeycloak().getTokenCacheSkewSeconds(), 0);
            Instant expiresAt = Instant.now().plusSeconds(Math.max(response.expiresIn() - skew, 1));
            return new CachedToken(response.accessToken(), expiresAt);
        } catch (RestClientResponseException exception) {
            throw new ProvisioningException(
                    HttpStatus.BAD_GATEWAY,
                    "KEYCLOAK_TOKEN_REQUEST_FAILED",
                    "Keycloak admin access token 발급에 실패했습니다. " + upstreamBody(exception),
                    exception
            );
        }
    }

    private String upstreamBody(RestClientResponseException exception) {
        String body = exception.getResponseBodyAsString();
        if (!StringUtils.hasText(body)) {
            return "status=" + exception.getStatusCode().value();
        }
        return body.length() > 500 ? body.substring(0, 500) : body;
    }

    private record CachedToken(
            String accessToken,
            Instant expiresAt
    ) {
        boolean isUsable() {
            return Instant.now().isBefore(expiresAt);
        }
    }
}
