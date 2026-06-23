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
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
        validatePasswordPolicy(request.password());
        String username = loginId(request.employeeNumber());
        KeycloakModels.UserRepresentation keycloakRequest = toKeycloakRequest(request, username);
        String keycloakUserId = keycloakAdminClient.createUser(keycloakRequest);

        try {
            ScimModels.ScimUserResponse scim = scimClient.create(toScimRequest(keycloakUserId, request, username));
            return new AdminUserResponses.ProvisionedUserResponse(
                    keycloakUserId,
                    scim == null ? null : scim.id(),
                    username,
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

    public AdminUserResponses.BulkProvisionedUsersResponse createBulk(
            AdminUserRequests.BulkCreateUsersRequest request
    ) {
        List<AdminUserRequests.CreateUserRequest> users = request.users();
        validateBulkUsers(users);

        List<AdminUserResponses.ProvisionedUserResponse> created = new ArrayList<>();
        try {
            for (AdminUserRequests.CreateUserRequest user : users) {
                created.add(create(user));
            }

            return new AdminUserResponses.BulkProvisionedUsersResponse(
                    users.size(),
                    created,
                    "CREATED"
            );
        } catch (RuntimeException exception) {
            String rollback = compensateBulkCreatedUsers(created);
            throw new ProvisioningException(
                    exception instanceof ProvisioningException provisioningException
                            ? provisioningException.getStatus()
                            : HttpStatus.BAD_GATEWAY,
                    "BULK_CREATE_FAILED_ROLLED_BACK",
                    "대량 직원 추가 중 실패해 이미 생성된 직원을 롤백했습니다. rollback=" + rollback
                            + ", cause=" + exception.getMessage(),
                    exception
            );
        }
    }

    public AdminUserResponses.ProvisionedUserResponse update(
            String keycloakUserId,
            AdminUserRequests.UpdateUserRequest request
    ) {
        if (StringUtils.hasText(request.password())) {
            validatePasswordPolicy(request.password());
        }
        String username = loginId(request.employeeNumber());
        keycloakAdminClient.updateUser(keycloakUserId, toKeycloakRequest(request, username));

        try {
            ScimModels.ScimUserRequest scimRequest = toScimRequest(keycloakUserId, request, username);
            ScimModels.ScimUserResponse scim = scimClient.findByExternalId(keycloakUserId)
                    .map(existing -> scimClient.update(existing.id(), scimRequest))
                    .orElseGet(() -> scimClient.create(scimRequest));

            return new AdminUserResponses.ProvisionedUserResponse(
                    keycloakUserId,
                    scim == null ? null : scim.id(),
                    username,
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

    private void validateBulkUsers(List<AdminUserRequests.CreateUserRequest> users) {
        Set<String> employeeNumbers = new LinkedHashSet<>();
        List<String> duplicates = new ArrayList<>();

        for (AdminUserRequests.CreateUserRequest user : users) {
            if (user == null || !StringUtils.hasText(user.employeeNumber())) {
                continue;
            }
            String employeeNumber = user.employeeNumber().trim();
            if (!employeeNumbers.add(employeeNumber)) {
                duplicates.add(employeeNumber);
            }
            validatePasswordPolicy(user.password());
        }

        if (!duplicates.isEmpty()) {
            throw new ProvisioningException(
                    HttpStatus.BAD_REQUEST,
                    "BULK_DUPLICATE_EMPLOYEE_NUMBER",
                    "대량 추가 목록에 중복 사번이 있습니다: " + String.join(", ", duplicates)
            );
        }
    }

    private void validatePasswordPolicy(String password) {
        List<String> violations = new ArrayList<>();
        String value = password == null ? "" : password.trim();

        if (value.length() < 8) {
            violations.add("8자 이상");
        }
        if (!value.matches(".*\\d.*")) {
            violations.add("숫자 1개 이상");
        }
        if (!value.matches(".*[A-Za-z].*")) {
            violations.add("영문 1개 이상");
        }

        if (!violations.isEmpty()) {
            throw new ProvisioningException(
                    HttpStatus.BAD_REQUEST,
                    "PASSWORD_POLICY_VIOLATION",
                    "비밀번호 정책을 만족하지 않습니다: " + String.join(", ", violations)
            );
        }
    }

    private String compensateBulkCreatedUsers(
            List<AdminUserResponses.ProvisionedUserResponse> created
    ) {
        if (created.isEmpty()) {
            return "NONE";
        }

        List<String> results = new ArrayList<>();
        for (int index = created.size() - 1; index >= 0; index--) {
            AdminUserResponses.ProvisionedUserResponse user = created.get(index);
            String username = user.username() == null ? "-" : user.username();

            if (StringUtils.hasText(user.scimUserId())) {
                try {
                    scimClient.deactivate(user.scimUserId());
                    results.add(username + ":SCIM_DEACTIVATED");
                } catch (ProvisioningException exception) {
                    results.add(username + ":SCIM_ROLLBACK_FAILED:" + exception.getCode());
                }
            }

            if (StringUtils.hasText(user.keycloakUserId())) {
                results.add(username + ":KEYCLOAK_" + compensateCreatedKeycloakUser(user.keycloakUserId()));
            }
        }

        return String.join("; ", results);
    }

    private KeycloakModels.UserRepresentation toKeycloakRequest(
            AdminUserRequests.CreateUserRequest request,
            String username
    ) {
        return new KeycloakModels.UserRepresentation(
                null,
                username,
                request.email(),
                null,
                null,
                request.enabled() == null || request.enabled(),
                Boolean.TRUE.equals(request.emailVerified()),
                attributes(
                        request.attributes(),
                        request.displayName(),
                        request.employeeNumber(),
                        request.position(),
                        request.role().name(),
                        request.tenancyType().name(),
                        request.tenancyName(),
                        employmentStatus(request.enabled(), request.sourceActive())
                ),
                credentials(request.password(), request.temporaryPassword())
        );
    }

    private KeycloakModels.UserRepresentation toKeycloakRequest(
            AdminUserRequests.UpdateUserRequest request,
            String username
    ) {
        return new KeycloakModels.UserRepresentation(
                null,
                username,
                request.email(),
                null,
                null,
                request.enabled() == null || request.enabled(),
                Boolean.TRUE.equals(request.emailVerified()),
                attributes(
                        request.attributes(),
                        request.displayName(),
                        request.employeeNumber(),
                        request.position(),
                        request.role().name(),
                        request.tenancyType().name(),
                        request.tenancyName(),
                        employmentStatus(request.enabled(), request.sourceActive())
                ),
                credentials(request.password(), request.temporaryPassword())
        );
    }

    private ScimModels.ScimUserRequest toScimRequest(
            String keycloakUserId,
            AdminUserRequests.CreateUserRequest request,
            String username
    ) {
        return scimRequest(
                keycloakUserId,
                username,
                request.email(),
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
            AdminUserRequests.UpdateUserRequest request,
            String username
    ) {
        return scimRequest(
                keycloakUserId,
                username,
                request.email(),
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
            String requestedDisplayName,
            String employeeNumber,
            String position,
            String role,
            String tenancyType,
            String tenancyName,
            Boolean sourceActive
    ) {
        String displayName = displayName(username, requestedDisplayName);
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

    private String loginId(String employeeNumber) {
        if (!StringUtils.hasText(employeeNumber)) {
            throw new ProvisioningException(
                    HttpStatus.BAD_REQUEST,
                    "LOGIN_ID_REQUIRED",
                    "사번은 Keycloak 로그인 ID로 사용되므로 필수입니다."
            );
        }
        return employeeNumber.trim();
    }

    private Map<String, List<String>> attributes(
            Map<String, List<String>> requestAttributes,
            String displayName,
            String employeeNumber,
            String position,
            String role,
            String tenancyType,
            String tenancyName,
            String employmentStatus
    ) {
        Map<String, List<String>> attributes = new LinkedHashMap<>();
        if (requestAttributes != null) {
            attributes.putAll(requestAttributes);
        }
        putAttribute(attributes, "displayName", displayName);
        putAttribute(attributes, "name", displayName);
        putAttribute(attributes, "employee_number", employeeNumber);
        putAttribute(attributes, "employeeNumber", employeeNumber);
        putAttribute(attributes, "position", position);
        putAttribute(attributes, "role", role);
        putAttribute(attributes, "erpRole", role);
        putAttribute(attributes, "tenancy_type", tenancyType);
        putAttribute(attributes, "tenancyType", tenancyType);
        putAttribute(attributes, "tenancy_name", tenancyName);
        putAttribute(attributes, "tenancyName", tenancyName);
        putAttribute(attributes, "employment_status", employmentStatus);
        return attributes;
    }

    private String employmentStatus(Boolean enabled, Boolean sourceActive) {
        return Boolean.FALSE.equals(enabled) || Boolean.FALSE.equals(sourceActive)
                ? "INACTIVE"
                : "ACTIVE";
    }

    private void putAttribute(Map<String, List<String>> attributes, String key, String value) {
        if (StringUtils.hasText(value)) {
            attributes.put(key, new ArrayList<>(List.of(value)));
        }
    }

    private String displayName(
            String username,
            String requestedDisplayName
    ) {
        if (StringUtils.hasText(requestedDisplayName)) {
            return requestedDisplayName;
        }

        return username;
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

    private AdminUserResponses.ScimUserSummary toScimSummary(ScimModels.ScimUserResponse scim) {
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
                scim.title(),
                role,
                tenancyType,
                tenancyName,
                scim.active()
        );
    }
}
