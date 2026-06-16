package com.hd.hdp.provisioning.keycloak;

import com.hd.hdp.provisioning.config.ProvisioningProperties;
import com.hd.hdp.provisioning.error.ProvisioningException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.URI;
import java.util.Arrays;
import java.util.List;

@Component
public class KeycloakAdminClient {

    private final RestClient keycloakRestClient;
    private final KeycloakAdminTokenClient tokenClient;
    private final ProvisioningProperties properties;

    public KeycloakAdminClient(
            @Qualifier("keycloakRestClient") RestClient keycloakRestClient,
            KeycloakAdminTokenClient tokenClient,
            ProvisioningProperties properties
    ) {
        this.keycloakRestClient = keycloakRestClient;
        this.tokenClient = tokenClient;
        this.properties = properties;
    }

    public String createUser(KeycloakModels.UserRepresentation request) {
        try {
            ResponseEntity<Void> response = keycloakRestClient
                    .post()
                    .uri("/admin/realms/{realm}/users", realm())
                    .headers(headers -> headers.setBearerAuth(tokenClient.getAccessToken()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .toBodilessEntity();

            return userIdFromLocation(response.getHeaders().getLocation());
        } catch (RestClientResponseException exception) {
            throw upstreamException("KEYCLOAK_CREATE_USER_FAILED", "Keycloak 사용자 생성에 실패했습니다.", exception);
        }
    }

    public KeycloakModels.UserRepresentation getUser(String keycloakUserId) {
        try {
            return keycloakRestClient
                    .get()
                    .uri("/admin/realms/{realm}/users/{userId}", realm(), keycloakUserId)
                    .headers(headers -> headers.setBearerAuth(tokenClient.getAccessToken()))
                    .retrieve()
                    .body(KeycloakModels.UserRepresentation.class);
        } catch (RestClientResponseException exception) {
            throw upstreamException("KEYCLOAK_GET_USER_FAILED", "Keycloak 사용자 조회에 실패했습니다.", exception);
        }
    }

    public List<KeycloakModels.UserRepresentation> searchUsers(String search, int first, int max) {
        try {
            KeycloakModels.UserRepresentation[] response = keycloakRestClient
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/admin/realms/{realm}/users")
                            .queryParamIfPresent("search", optionalText(search))
                            .queryParam("first", Math.max(first, 0))
                            .queryParam("max", Math.max(Math.min(max, 100), 1))
                            .build(realm()))
                    .headers(headers -> headers.setBearerAuth(tokenClient.getAccessToken()))
                    .retrieve()
                    .body(KeycloakModels.UserRepresentation[].class);

            return response == null ? List.of() : Arrays.asList(response);
        } catch (RestClientResponseException exception) {
            throw upstreamException("KEYCLOAK_SEARCH_USERS_FAILED", "Keycloak 사용자 검색에 실패했습니다.", exception);
        }
    }

    public void updateUser(String keycloakUserId, KeycloakModels.UserRepresentation request) {
        try {
            keycloakRestClient
                    .put()
                    .uri("/admin/realms/{realm}/users/{userId}", realm(), keycloakUserId)
                    .headers(headers -> headers.setBearerAuth(tokenClient.getAccessToken()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request.withoutCredentials())
                    .retrieve()
                    .toBodilessEntity();

            if (request.credentials() != null && !request.credentials().isEmpty()) {
                resetPassword(keycloakUserId, request.credentials().get(0));
            }
        } catch (RestClientResponseException exception) {
            throw upstreamException("KEYCLOAK_UPDATE_USER_FAILED", "Keycloak 사용자 수정에 실패했습니다.", exception);
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
                null
        ));
    }

    public void deleteUser(String keycloakUserId) {
        try {
            keycloakRestClient
                    .delete()
                    .uri("/admin/realms/{realm}/users/{userId}", realm(), keycloakUserId)
                    .headers(headers -> headers.setBearerAuth(tokenClient.getAccessToken()))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException exception) {
            throw upstreamException("KEYCLOAK_DELETE_USER_FAILED", "Keycloak 사용자 삭제에 실패했습니다.", exception);
        }
    }

    private void resetPassword(
            String keycloakUserId,
            KeycloakModels.CredentialRepresentation credential
    ) {
        keycloakRestClient
                .put()
                .uri("/admin/realms/{realm}/users/{userId}/reset-password", realm(), keycloakUserId)
                .headers(headers -> headers.setBearerAuth(tokenClient.getAccessToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .body(credential)
                .retrieve()
                .toBodilessEntity();
    }

    private String userIdFromLocation(URI location) {
        if (location == null) {
            throw new ProvisioningException(
                    HttpStatus.BAD_GATEWAY,
                    "KEYCLOAK_CREATE_LOCATION_MISSING",
                    "Keycloak 사용자 생성 응답에 Location 헤더가 없습니다."
            );
        }

        String path = location.getPath();
        int index = path.lastIndexOf('/');
        return index < 0 ? path : path.substring(index + 1);
    }

    private java.util.Optional<String> optionalText(String value) {
        return StringUtils.hasText(value) ? java.util.Optional.of(value) : java.util.Optional.empty();
    }

    private ProvisioningException upstreamException(
            String code,
            String message,
            RestClientResponseException exception
    ) {
        HttpStatus status = switch (exception.getStatusCode().value()) {
            case 400 -> HttpStatus.BAD_REQUEST;
            case 401 -> HttpStatus.BAD_GATEWAY;
            case 403 -> HttpStatus.BAD_GATEWAY;
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

    private String realm() {
        return properties.getKeycloak().getRealm();
    }
}
