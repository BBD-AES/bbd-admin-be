package com.hd.hdp.provisioning.scim;

import com.hd.hdp.provisioning.config.ProvisioningProperties;
import com.hd.hdp.provisioning.error.ProvisioningException;
import com.hd.hdp.provisioning.keycloak.KeycloakModels;
import com.hd.hdp.provisioning.keycloak.KeycloakTokenHttpService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientResponseException;

import java.time.Instant;

@Component
public class ScimClientCredentialsTokenClient {

    private final KeycloakTokenHttpService tokenHttpService;
    private final ProvisioningProperties properties;
    private volatile CachedToken cachedToken;

    public ScimClientCredentialsTokenClient(
            KeycloakTokenHttpService tokenHttpService,
            ProvisioningProperties properties
    ) {
        this.tokenHttpService = tokenHttpService;
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
        ProvisioningProperties.Scim scim = properties.getScim();
        if (!StringUtils.hasText(scim.getClientId()) || !StringUtils.hasText(scim.getClientSecret())) {
            throw new ProvisioningException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "SCIM_CLIENT_CREDENTIALS_REQUIRED",
                    "USER_SCIM_CLIENT_ID and USER_SCIM_CLIENT_SECRET are required."
            );
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", scim.getClientId());
        form.add("client_secret", scim.getClientSecret());

        try {
            KeycloakModels.TokenResponse response = tokenHttpService.requestToken(
                    properties.getKeycloak().getRealm(),
                    form
            );

            if (response == null || !StringUtils.hasText(response.accessToken())) {
                throw new ProvisioningException(
                        HttpStatus.BAD_GATEWAY,
                        "SCIM_TOKEN_EMPTY",
                        "SCIM client credentials access token response was empty."
                );
            }

            long skew = Math.max(scim.getTokenCacheSkewSeconds(), 0);
            Instant expiresAt = Instant.now().plusSeconds(Math.max(response.expiresIn() - skew, 1));
            return new CachedToken(response.accessToken(), expiresAt);
        } catch (RestClientResponseException exception) {
            throw new ProvisioningException(
                    HttpStatus.BAD_GATEWAY,
                    "SCIM_TOKEN_REQUEST_FAILED",
                    "Failed to issue SCIM client credentials access token. " + upstreamBody(exception),
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
