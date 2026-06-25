package com.hd.hdp.provisioning.keycloak;

import com.hd.hdp.provisioning.config.ProvisioningProperties;
import com.hd.hdp.provisioning.error.ProvisioningException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientResponseException;

import java.net.URI;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class KeycloakAdminClient {

    private final KeycloakAdminHttpService keycloakAdminHttpService;
    private final KeycloakRealmAdminHttpService keycloakRealmAdminHttpService;
    private final KeycloakAdminTokenClient tokenClient;
    private final ProvisioningProperties properties;

    public KeycloakAdminClient(
            KeycloakAdminHttpService keycloakAdminHttpService,
            KeycloakRealmAdminHttpService keycloakRealmAdminHttpService,
            KeycloakAdminTokenClient tokenClient,
            ProvisioningProperties properties
    ) {
        this.keycloakAdminHttpService = keycloakAdminHttpService;
        this.keycloakRealmAdminHttpService = keycloakRealmAdminHttpService;
        this.tokenClient = tokenClient;
        this.properties = properties;
    }

    public String createUser(KeycloakModels.UserRepresentation request) {
        try {
            ResponseEntity<Void> response = keycloakAdminHttpService.createUser(
                    realm(),
                    authorization(),
                    request
            );

            return userIdFromLocation(response.getHeaders().getLocation());
        } catch (RestClientResponseException exception) {
            throw upstreamException("KEYCLOAK_CREATE_USER_FAILED", "Failed to create Keycloak user.", exception);
        }
    }

    public KeycloakModels.UserRepresentation getUser(String keycloakUserId) {
        try {
            return keycloakAdminHttpService.getUser(realm(), keycloakUserId, authorization());
        } catch (RestClientResponseException exception) {
            throw upstreamException("KEYCLOAK_GET_USER_FAILED", "Failed to get Keycloak user.", exception);
        }
    }

    public List<KeycloakModels.UserRepresentation> searchUsers(String search, int first, int max) {
        try {
            int safeFirst = Math.max(first, 0);
            int safeMax = Math.max(Math.min(max, 100), 1);
            KeycloakModels.UserRepresentation[] response = StringUtils.hasText(search)
                    ? keycloakAdminHttpService.searchUsers(
                            realm(),
                            authorization(),
                            search,
                            safeFirst,
                            safeMax
                    )
                    : keycloakAdminHttpService.searchUsers(
                            realm(),
                            authorization(),
                            safeFirst,
                            safeMax
                    );

            return response == null ? List.of() : Arrays.asList(response);
        } catch (RestClientResponseException exception) {
            throw upstreamException("KEYCLOAK_SEARCH_USERS_FAILED", "Failed to search Keycloak users.", exception);
        }
    }

    public void updateUser(String keycloakUserId, KeycloakModels.UserRepresentation request) {
        try {
            keycloakAdminHttpService.updateUser(
                    realm(),
                    keycloakUserId,
                    authorization(),
                    request.withoutCredentials()
            );

            if (request.credentials() != null && !request.credentials().isEmpty()) {
                resetPassword(keycloakUserId, request.credentials().get(0));
            }
        } catch (RestClientResponseException exception) {
            throw upstreamException("KEYCLOAK_UPDATE_USER_FAILED", "Failed to update Keycloak user.", exception);
        }
    }

    public void updateUserProfile(String keycloakUserId, KeycloakModels.UserRepresentation request) {
        try {
            keycloakAdminHttpService.updateUser(
                    realm(),
                    keycloakUserId,
                    authorization(),
                    request.withoutCredentials()
            );
        } catch (RestClientResponseException exception) {
            throw upstreamException("KEYCLOAK_UPDATE_USER_FAILED", "Failed to update Keycloak user.", exception);
        }
    }

    public void disableUser(String keycloakUserId) {
        KeycloakModels.UserRepresentation current = getUser(keycloakUserId);
        updateUser(keycloakUserId, new KeycloakModels.UserRepresentation(
                current.id(),
                current.username(),
                current.email(),
                current.firstName(),
                current.lastName(),
                false,
                current.emailVerified(),
                current.attributes(),
                null,
                current.requiredActions()
        ));
    }

    public void deleteUser(String keycloakUserId) {
        try {
            keycloakAdminHttpService.deleteUser(realm(), keycloakUserId, authorization());
        } catch (RestClientResponseException exception) {
            throw upstreamException("KEYCLOAK_DELETE_USER_FAILED", "Failed to delete Keycloak user.", exception);
        }
    }

    public KeycloakModels.PasswordLockPolicy getPasswordLockPolicy() {
        try {
            Map<String, Object> realm = keycloakRealmAdminHttpService.getRealm(realm(), authorization());
            return new KeycloakModels.PasswordLockPolicy(
                    Boolean.TRUE.equals(realm.get("bruteForceProtected")),
                    integerValue(realm.get("failureFactor"))
            );
        } catch (RestClientResponseException exception) {
            throw upstreamException(
                    "KEYCLOAK_GET_REALM_FAILED",
                    "Failed to get Keycloak realm.",
                    exception
            );
        }
    }

    public KeycloakModels.PasswordLockPolicy updatePasswordLockPolicy(boolean enabled) {
        try {
            Map<String, Object> request = new LinkedHashMap<>();
            request.put("bruteForceProtected", enabled);
            request.put("permanentLockout", enabled);
            request.put("failureFactor", 5);
            keycloakRealmAdminHttpService.updateRealm(realm(), authorization(), request);
            return new KeycloakModels.PasswordLockPolicy(enabled, 5);
        } catch (RestClientResponseException exception) {
            throw upstreamException(
                    "KEYCLOAK_UPDATE_REALM_FAILED",
                    "Failed to update Keycloak realm.",
                    exception
            );
        }
    }

    public void unlockUser(String keycloakUserId) {
        try {
            keycloakRealmAdminHttpService.unlockUser(realm(), keycloakUserId, authorization());
        } catch (RestClientResponseException exception) {
            throw upstreamException("KEYCLOAK_UNLOCK_USER_FAILED", "Failed to unlock Keycloak user.", exception);
        }
    }

    private void resetPassword(
            String keycloakUserId,
            KeycloakModels.CredentialRepresentation credential
    ) {
        keycloakAdminHttpService.resetPassword(realm(), keycloakUserId, authorization(), credential);
    }

    private String userIdFromLocation(URI location) {
        if (location == null) {
            throw new ProvisioningException(
                    HttpStatus.BAD_GATEWAY,
                    "KEYCLOAK_CREATE_LOCATION_MISSING",
                    "Keycloak user create response did not contain a Location header."
            );
        }

        String path = location.getPath();
        int index = path.lastIndexOf('/');
        return index < 0 ? path : path.substring(index + 1);
    }

    private ProvisioningException upstreamException(
            String code,
            String message,
            RestClientResponseException exception
    ) {
        HttpStatus status = switch (exception.getStatusCode().value()) {
            case 400 -> HttpStatus.BAD_REQUEST;
            case 401, 403 -> HttpStatus.BAD_GATEWAY;
            case 404 -> HttpStatus.NOT_FOUND;
            case 409 -> HttpStatus.CONFLICT;
            default -> HttpStatus.BAD_GATEWAY;
        };

        return new ProvisioningException(
                status,
                code,
                message + " " + upstreamBody(exception),
                exception
        );
    }

    private String upstreamBody(RestClientResponseException exception) {
        String body = exception.getResponseBodyAsString();
        if (!StringUtils.hasText(body)) {
            return "status=" + exception.getStatusCode().value();
        }
        return body.length() > 500 ? body.substring(0, 500) : body;
    }

    private Integer integerValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            return Integer.parseInt(text);
        }
        return null;
    }

    private String realm() {
        return properties.getKeycloak().getRealm();
    }

    private String authorization() {
        return "Bearer " + tokenClient.getAccessToken();
    }
}
