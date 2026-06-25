package com.hd.hdp.provisioning.scim;

import com.hd.hdp.provisioning.config.ProvisioningProperties;
import com.hd.hdp.provisioning.error.ProvisioningException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
public class ScimClient {

    private final ScimHttpService scimHttpService;
    private final ProvisioningProperties properties;
    private final ScimClientCredentialsTokenClient tokenClient;

    public ScimClient(
            ScimHttpService scimHttpService,
            ProvisioningProperties properties,
            ScimClientCredentialsTokenClient tokenClient
    ) {
        this.scimHttpService = scimHttpService;
        this.properties = properties;
        this.tokenClient = tokenClient;
    }

    public ScimModels.ScimUserResponse create(ScimModels.ScimUserRequest request) {
        try {
            return scimHttpService.create(mutationHeaders(), request);
        } catch (RestClientResponseException exception) {
            throw upstreamException("SCIM_CREATE_USER_FAILED", "Failed to create SCIM user.", exception);
        } catch (ResourceAccessException exception) {
            throw connectionException(exception);
        }
    }

    public Optional<ScimModels.ScimUserResponse> findByExternalId(String externalId) {
        try {
            ScimModels.ScimListResponse response = scimHttpService.search(
                    authHeaders(),
                    "externalId eq \"" + externalId + "\"",
                    1,
                    1
            );

            if (response == null || response.safeResources().isEmpty()) {
                return Optional.empty();
            }

            return Optional.of(response.safeResources().get(0));
        } catch (RestClientResponseException exception) {
            throw upstreamException("SCIM_SEARCH_USER_FAILED", "Failed to search SCIM user.", exception);
        } catch (ResourceAccessException exception) {
            throw connectionException(exception);
        }
    }

    public ScimModels.ScimUserResponse update(String scimUserId, ScimModels.ScimUserRequest request) {
        try {
            return scimHttpService.update(mutationHeaders(), scimUserId, request);
        } catch (RestClientResponseException exception) {
            throw upstreamException("SCIM_UPDATE_USER_FAILED", "Failed to update SCIM user.", exception);
        } catch (ResourceAccessException exception) {
            throw connectionException(exception);
        }
    }

    public void deactivate(String scimUserId) {
        try {
            scimHttpService.delete(mutationHeaders(), scimUserId);
        } catch (RestClientResponseException exception) {
            throw upstreamException("SCIM_DEACTIVATE_USER_FAILED", "Failed to deactivate SCIM user.", exception);
        } catch (ResourceAccessException exception) {
            throw connectionException(exception);
        }
    }

    private Map<String, String> authHeaders() {
        ProvisioningProperties.Scim scim = properties.getScim();
        Map<String, String> headers = new LinkedHashMap<>();
        switch (scim.getAuthMode()) {
            case BEARER -> headers.put(HttpHeaders.AUTHORIZATION, "Bearer " + scim.getBearerToken());
            case BASIC -> headers.put(
                    HttpHeaders.AUTHORIZATION,
                    "Basic " + HttpHeaders.encodeBasicAuth(
                            scim.getBasicUsername(),
                            scim.getBasicPassword(),
                            StandardCharsets.UTF_8
                    )
            );
            case API_KEY -> headers.put(scim.getApiKeyHeader(), scim.getApiKey());
            case CLIENT_CREDENTIALS -> headers.put(
                    HttpHeaders.AUTHORIZATION,
                    "Bearer " + tokenClient.getAccessToken()
            );
            case MTLS, NONE -> {
            }
        }
        return headers;
    }

    private Map<String, String> mutationHeaders() {
        Map<String, String> headers = authHeaders();
        headers.put("Idempotency-Key", UUID.randomUUID().toString());
        return headers;
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

    private ProvisioningException connectionException(ResourceAccessException exception) {
        return new ProvisioningException(
                HttpStatus.BAD_GATEWAY,
                "SCIM_CONNECTION_FAILED",
                "User 서비스 SCIM API에 연결할 수 없습니다. USER_SCIM_BASE_URL="
                        + properties.getScim().getBaseUrl(),
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
}
