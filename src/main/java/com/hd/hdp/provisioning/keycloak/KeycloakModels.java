package com.hd.hdp.provisioning.keycloak;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

public final class KeycloakModels {

    private KeycloakModels() {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("expires_in") long expiresIn
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UserRepresentation(
            String id,
            String username,
            String email,
            String firstName,
            String lastName,
            Boolean enabled,
            Boolean emailVerified,
            Map<String, List<String>> attributes,
            List<CredentialRepresentation> credentials
    ) {
        public UserRepresentation withoutCredentials() {
            return new UserRepresentation(
                    id,
                    username,
                    email,
                    firstName,
                    lastName,
                    enabled,
                    emailVerified,
                    attributes,
                    null
            );
        }
    }

    public record CredentialRepresentation(
            String type,
            String value,
            Boolean temporary
    ) {
        public static CredentialRepresentation password(String value, boolean temporary) {
            return new CredentialRepresentation("password", value, temporary);
        }
    }
}
