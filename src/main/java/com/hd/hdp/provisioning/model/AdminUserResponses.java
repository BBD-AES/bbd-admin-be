package com.hd.hdp.provisioning.model;

import java.util.List;
import java.util.Map;

public final class AdminUserResponses {

    private AdminUserResponses() {
    }

    public record ProvisionedUserResponse(
            String keycloakUserId,
            String scimUserId,
            String username,
            String email,
            String result
    ) {
    }

    public record AdminUserDetailResponse(
            KeycloakUserSummary keycloak,
            ScimUserSummary scim
    ) {
    }

    public record KeycloakUserSummary(
            String id,
            String username,
            String email,
            String firstName,
            String lastName,
            Boolean enabled,
            Boolean emailVerified,
            Map<String, List<String>> attributes
    ) {
    }

    public record ScimUserSummary(
            String id,
            String externalId,
            String userName,
            String displayName,
            String employeeNumber,
            String role,
            String tenancyType,
            String tenancyName,
            Boolean active
    ) {
    }
}
