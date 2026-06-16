package com.hd.hdp.provisioning.service;

import com.hd.hdp.provisioning.config.ProvisioningProperties;
import com.hd.hdp.provisioning.error.ProvisioningException;
import com.hd.hdp.provisioning.keycloak.KeycloakAdminClient;
import com.hd.hdp.provisioning.keycloak.KeycloakModels;
import com.hd.hdp.provisioning.model.AdminUserRequests;
import com.hd.hdp.provisioning.model.AdminUserResponses;
import com.hd.hdp.provisioning.scim.ScimClient;
import com.hd.hdp.provisioning.scim.ScimModels;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AdminUserProvisioningService {

    private final KeycloakAdminClient keycloakAdminClient;
    private final ScimClient scimClient;
    private final ProvisioningProperties properties;

    public AdminUserProvisioningService(
            KeycloakAdminClient keycloakAdminClient,
            ScimClient scimClient,
            ProvisioningProperties properties
    ) {
        this.keycloakAdminClient = keycloakAdminClient;
        this.scimClient = scimClient;
        this.properties = properties;
    }

    public AdminUserResponses.ProvisionedUserResponse create(AdminUserRequests.CreateUserRequest request) {
        KeycloakModels.UserRepresentation keycloakRequest = toKeycloakRequest(request);
        String keycloakUserId = keycloakAdminClient.createUser(keycloakRequest);

        try {
            ScimModels.ScimUserResponse scim = scimClient.create(toScimRequest(keycloakUserId, request));
            return new AdminUserResponses.ProvisionedUserResponse(
                    keycloakUserId,
                    scim == null ? null : scim.id(),
                    request.username(),
                    request.email(),
                    "CREATED"
            );
        } catch (ProvisioningException exception) {
            String compensation = compensateCreatedKeycloakUser(keycloakUserId);
            throw new ProvisioningException(
                    HttpStatus.BAD_GATEWAY,
                    "SCIM_CREATE_FAILED_AFTER_KEYCLOAK_CREATE",
                    "Keycloak 사용자는 생성됐지만 SCIM 사용자 생성이 실패했습니다. compensation=" + compensation
                            + ", keycloakUserId=" + keycloakUserId
                            + ", cause=" + exception.getMessage(),
                    exception
            );
        }
    }

    public AdminUserResponses.ProvisionedUserResponse update(
            String keycloakUserId,
            AdminUserRequests.UpdateUserRequest request
    ) {
        keycloakAdminClient.updateUser(keycloakUserId, toKeycloakRequest(request));

        try {
            ScimModels.ScimUserRequest scimRequest = toScimRequest(keycloakUserId, request);
            ScimModels.ScimUserResponse scim = scimClient.findByExternalId(keycloakUserId)
                    .map(existing -> scimClient.update(existing.id(), scimRequest))
                    .orElseGet(() -> scimClient.create(scimRequest));

            return new AdminUserResponses.ProvisionedUserResponse(
                    keycloakUserId,
                    scim == null ? null : scim.id(),
                    request.username(),
                    request.email(),
                    "UPDATED"
            );
        } catch (ProvisioningException exception) {
            throw new ProvisioningException(
                    HttpStatus.BAD_GATEWAY,
                    "SCIM_UPDATE_FAILED_AFTER_KEYCLOAK_UPDATE",
                    "Keycloak 사용자는 수정됐지만 SCIM 사용자 반영이 실패했습니다. "
                            + "수정 롤백은 자동 수행하지 않았습니다. keycloakUserId=" + keycloakUserId
                            + ", cause=" + exception.getMessage(),
                    exception
            );
        }
    }

    public AdminUserResponses.ProvisionedUserResponse deactivate(String keycloakUserId) {
        KeycloakModels.UserRepresentation keycloak = keycloakAdminClient.getUser(keycloakUserId);
        keycloakAdminClient.disableUser(keycloakUserId);

        String scimUserId = scimClient.findByExternalId(keycloakUserId)
                .map(scim -> {
                    scimClient.deactivate(scim.id());
                    return scim.id();
                })
                .orElse(null);

        return new AdminUserResponses.ProvisionedUserResponse(
                keycloakUserId,
                scimUserId,
                keycloak == null ? null : keycloak.username(),
                keycloak == null ? null : keycloak.email(),
                "DEACTIVATED"
        );
    }

    public List<AdminUserResponses.KeycloakUserSummary> search(String search, int first, int max) {
        return keycloakAdminClient.searchUsers(search, first, max)
                .stream()
                .map(this::toKeycloakSummary)
                .toList();
    }

    public AdminUserResponses.AdminUserDetailResponse get(String keycloakUserId) {
        KeycloakModels.UserRepresentation keycloak = keycloakAdminClient.getUser(keycloakUserId);
        AdminUserResponses.ScimUserSummary scim = scimClient.findByExternalId(keycloakUserId)
                .map(this::toScimSummary)
                .orElse(null);

        return new AdminUserResponses.AdminUserDetailResponse(
                toKeycloakSummary(keycloak),
                scim
        );
    }

    private String compensateCreatedKeycloakUser(String keycloakUserId) {
        try {
            return switch (properties.getCompensation().getCreateFailureMode()) {
                case NONE -> "NONE";
                case DISABLE -> {
                    keycloakAdminClient.disableUser(keycloakUserId);
                    yield "DISABLED";
                }
                case DELETE -> {
                    keycloakAdminClient.deleteUser(keycloakUserId);
                    yield "DELETED";
                }
            };
        } catch (ProvisioningException compensationException) {
            return "FAILED:" + compensationException.getMessage();
        }
    }

    private KeycloakModels.UserRepresentation toKeycloakRequest(AdminUserRequests.CreateUserRequest request) {
        return new KeycloakModels.UserRepresentation(
                null,
                request.username(),
                request.email(),
                request.firstName(),
                request.lastName(),
                request.enabled() == null || request.enabled(),
                Boolean.TRUE.equals(request.emailVerified()),
                attributes(
                        request.attributes(),
                        request.employeeNumber(),
                        request.role().name(),
                        request.tenancyType().name(),
                        request.tenancyName()
                ),
                credentials(request.password(), request.temporaryPassword())
        );
    }

    private KeycloakModels.UserRepresentation toKeycloakRequest(AdminUserRequests.UpdateUserRequest request) {
        return new KeycloakModels.UserRepresentation(
                null,
                request.username(),
                request.email(),
                request.firstName(),
                request.lastName(),
                request.enabled() == null || request.enabled(),
                Boolean.TRUE.equals(request.emailVerified()),
                attributes(
                        request.attributes(),
                        request.employeeNumber(),
                        request.role().name(),
                        request.tenancyType().name(),
                        request.tenancyName()
                ),
                credentials(request.password(), request.temporaryPassword())
        );
    }

    private ScimModels.ScimUserRequest toScimRequest(
            String keycloakUserId,
            AdminUserRequests.CreateUserRequest request
    ) {
        return scimRequest(
                keycloakUserId,
                request.username(),
                request.email(),
                request.firstName(),
                request.lastName(),
                request.displayName(),
                request.employeeNumber(),
                request.position(),
                request.role().name(),
                request.tenancyType().name(),
                request.tenancyName(),
                request.sourceActive()
        );
    }

    private ScimModels.ScimUserRequest toScimRequest(
            String keycloakUserId,
            AdminUserRequests.UpdateUserRequest request
    ) {
        return scimRequest(
                keycloakUserId,
                request.username(),
                request.email(),
                request.firstName(),
                request.lastName(),
                request.displayName(),
                request.employeeNumber(),
                request.position(),
                request.role().name(),
                request.tenancyType().name(),
                request.tenancyName(),
                request.sourceActive()
        );
    }

    private ScimModels.ScimUserRequest scimRequest(
            String keycloakUserId,
            String username,
            String email,
            String firstName,
            String lastName,
            String requestedDisplayName,
            String employeeNumber,
            String position,
            String role,
            String tenancyType,
            String tenancyName,
            Boolean sourceActive
    ) {
        String displayName = displayName(username, firstName, lastName, requestedDisplayName);
        List<ScimModels.ScimEmail> emails = StringUtils.hasText(email)
                ? List.of(new ScimModels.ScimEmail(email, "work", true))
                : List.of();

        return new ScimModels.ScimUserRequest(
                List.of(
                        ScimModels.CORE_USER_SCHEMA,
                        ScimModels.ENTERPRISE_USER_SCHEMA,
                        ScimModels.ERP_USER_SCHEMA
                ),
                keycloakUserId,
                username,
                displayName,
                new ScimModels.ScimName(displayName),
                position,
                emails,
                List.of(new ScimModels.ScimRole(role, role, true)),
                sourceActive == null || sourceActive,
                new ScimModels.EnterpriseExtension(employeeNumber, tenancyType, tenancyName),
                new ScimModels.ErpExtension(role, tenancyType, tenancyName)
        );
    }

    private List<KeycloakModels.CredentialRepresentation> credentials(
            String password,
            Boolean temporaryPassword
    ) {
        if (!StringUtils.hasText(password)) {
            return null;
        }
        return List.of(KeycloakModels.CredentialRepresentation.password(
                password,
                temporaryPassword == null || temporaryPassword
        ));
    }

    private Map<String, List<String>> attributes(
            Map<String, List<String>> requestAttributes,
            String employeeNumber,
            String role,
            String tenancyType,
            String tenancyName
    ) {
        Map<String, List<String>> attributes = new LinkedHashMap<>();
        if (requestAttributes != null) {
            attributes.putAll(requestAttributes);
        }
        putAttribute(attributes, "employeeNumber", employeeNumber);
        putAttribute(attributes, "erpRole", role);
        putAttribute(attributes, "tenancyType", tenancyType);
        putAttribute(attributes, "tenancyName", tenancyName);
        return attributes;
    }

    private void putAttribute(Map<String, List<String>> attributes, String key, String value) {
        if (StringUtils.hasText(value)) {
            attributes.put(key, new ArrayList<>(List.of(value)));
        }
    }

    private String displayName(
            String username,
            String firstName,
            String lastName,
            String requestedDisplayName
    ) {
        if (StringUtils.hasText(requestedDisplayName)) {
            return requestedDisplayName;
        }

        String fullName = String.join(
                " ",
                List.of(nullToBlank(firstName), nullToBlank(lastName))
        ).trim();

        return StringUtils.hasText(fullName) ? fullName : username;
    }

    private String nullToBlank(String value) {
        return value == null ? "" : value;
    }

    private AdminUserResponses.KeycloakUserSummary toKeycloakSummary(
            KeycloakModels.UserRepresentation user
    ) {
        if (user == null) {
            return null;
        }
        return new AdminUserResponses.KeycloakUserSummary(
                user.id(),
                user.username(),
                user.email(),
                user.firstName(),
                user.lastName(),
                user.enabled(),
                user.emailVerified(),
                user.attributes()
        );
    }

    private AdminUserResponses.ScimUserSummary toScimSummary(
            ScimModels.ScimUserResponse scim
    ) {
        if (scim == null) {
            return null;
        }

        String employeeNumber = scim.enterprise() == null ? null : scim.enterprise().employeeNumber();
        String role = scim.erp() == null ? null : scim.erp().role();
        String tenancyType = scim.erp() == null ? null : scim.erp().tenancyType();
        String tenancyName = scim.erp() == null ? null : scim.erp().tenancyName();

        return new AdminUserResponses.ScimUserSummary(
                scim.id(),
                scim.externalId(),
                scim.userName(),
                scim.displayName(),
                employeeNumber,
                role,
                tenancyType,
                tenancyName,
                scim.active()
        );
    }
}
