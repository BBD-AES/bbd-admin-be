package com.hd.hdp.provisioning.scim;

import com.hd.hdp.provisioning.config.ProvisioningProperties;
import com.hd.hdp.provisioning.error.ProvisioningException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

@Component
public class ScimClient {

    private static final MediaType SCIM_MEDIA_TYPE = MediaType.parseMediaType(ScimModels.MEDIA_TYPE);

    private final RestClient scimRestClient;
    private final ProvisioningProperties properties;

    public ScimClient(
            @Qualifier("scimRestClient") RestClient scimRestClient,
            ProvisioningProperties properties
    ) {
        this.scimRestClient = scimRestClient;
        this.properties = properties;
    }

    public ScimModels.ScimUserResponse create(ScimModels.ScimUserRequest request) {
        try {
            return scimRestClient
                    .post()
                    .uri("/scim/v2/Users")
                    .headers(this::applyAuth)
                    .contentType(SCIM_MEDIA_TYPE)
                    .accept(SCIM_MEDIA_TYPE, MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(ScimModels.ScimUserResponse.class);
        } catch (RestClientResponseException exception) {
            throw upstreamException("SCIM_CREATE_USER_FAILED", "SCIM 사용자 생성에 실패했습니다.", exception);
        }
    }

    public Optional<ScimModels.ScimUserResponse> findByExternalId(String externalId) {
        try {
            ScimModels.ScimListResponse response = scimRestClient
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/scim/v2/Users")
                            .queryParam("filter", "externalId eq \"" + externalId + "\"")
                            .queryParam("startIndex", 1)
                            .queryParam("count", 1)
                            .build())
                    .headers(this::applyAuth)
                    .accept(SCIM_MEDIA_TYPE, MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(ScimModels.ScimListResponse.class);

            if (response == null || response.safeResources().isEmpty()) {
                return Optional.empty();
            }

            return Optional.of(response.safeResources().get(0));
        } catch (RestClientResponseException exception) {
            throw upstreamException("SCIM_SEARCH_USER_FAILED", "SCIM 사용자 검색에 실패했습니다.", exception);
        }
    }

    public ScimModels.ScimUserResponse update(String scimUserId, ScimModels.ScimUserRequest request) {
        try {
            return scimRestClient
                    .put()
                    .uri("/scim/v2/Users/{userId}", scimUserId)
                    .headers(this::applyAuth)
                    .contentType(SCIM_MEDIA_TYPE)
                    .accept(SCIM_MEDIA_TYPE, MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(ScimModels.ScimUserResponse.class);
        } catch (RestClientResponseException exception) {
            throw upstreamException("SCIM_UPDATE_USER_FAILED", "SCIM 사용자 수정에 실패했습니다.", exception);
        }
    }

    public void deactivate(String scimUserId) {
        try {
            scimRestClient
                    .delete()
                    .uri("/scim/v2/Users/{userId}", scimUserId)
                    .headers(this::applyAuth)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException exception) {
            throw upstreamException("SCIM_DEACTIVATE_USER_FAILED", "SCIM 사용자 비활성화에 실패했습니다.", exception);
        }
    }

    private void applyAuth(HttpHeaders headers) {
        ProvisioningProperties.Scim scim = properties.getScim();
        switch (scim.getAuthMode()) {
            case BEARER -> headers.setBearerAuth(scim.getBearerToken());
            case BASIC -> headers.setBasicAuth(
                    scim.getBasicUsername(),
                    scim.getBasicPassword(),
                    StandardCharsets.UTF_8
            );
            case API_KEY -> headers.set(scim.getApiKeyHeader(), scim.getApiKey());
            case MTLS, NONE -> {
            }
        }
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
}
