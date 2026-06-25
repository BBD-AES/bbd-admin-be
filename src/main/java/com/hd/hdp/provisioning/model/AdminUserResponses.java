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
            Map<String, List<String>> attributes,
            List<String> requiredActions
    ) {
    }

    public record ScimUserSummary(
            String id,
            String externalId,
            String userName,
            String displayName,
            String employeeNumber,
            String position,
            String role,
            String tenancyType,
            String tenancyName,
            Boolean active
    ) {
    }

    public record NextEmployeeNumberResponse(
            String prefix,
            String nextNumber
    ) {
    }

    public record PasswordLockPolicyResponse(
            Boolean enabled,
            Integer failureFactor
    ) {
    }

    public record BulkProvisionedUsersResponse(
            int requested,
            List<ProvisionedUserResponse> users,
            String result
    ) {
    }

    public record UserMaintenanceResponse(
            int requested,
            int updated,
            int unchanged,
            List<String> failedUsers,
            String result
    ) {
    }
}
