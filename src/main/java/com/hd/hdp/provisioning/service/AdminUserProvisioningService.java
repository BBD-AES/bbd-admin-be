package com.hd.hdp.provisioning.service;

import com.hd.hdp.provisioning.config.ProvisioningProperties;
import com.hd.hdp.provisioning.error.ProvisioningException;
import com.hd.hdp.provisioning.keycloak.KeycloakAdminClient;
import com.hd.hdp.provisioning.keycloak.KeycloakModels;
import com.hd.hdp.provisioning.model.AdminUserRequests;
import com.hd.hdp.provisioning.model.AdminUserResponses;
import com.hd.hdp.provisioning.model.ProvisioningEnums;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AdminUserProvisioningService {

    private static final String CONFIGURE_TOTP_REQUIRED_ACTION = "CONFIGURE_TOTP";

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
        boolean autoEmployeeNumber =
                (request.autoEmployeeNumber() == null || request.autoEmployeeNumber())
                        && !StringUtils.hasText(request.employeeNumber());
        String autoPrefix = autoEmployeeNumber ? employeeNumberPrefix(request.role()) : null;
        int maxAttempts = autoEmployeeNumber ? 3 : 1;
        ProvisioningException lastConflict = null;

        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            AdminUserRequests.CreateUserRequest resolvedRequest = autoEmployeeNumber
                    ? withEmployeeNumber(request, nextEmployeeNumberValue(autoPrefix))
                    : withResolvedEmployeeNumber(request);
            String username = loginId(resolvedRequest.employeeNumber());
            KeycloakModels.UserRepresentation keycloakRequest = toKeycloakRequest(resolvedRequest, username);
            String keycloakUserId;
            try {
                keycloakUserId = keycloakAdminClient.createUser(keycloakRequest);
            } catch (ProvisioningException exception) {
                if (autoEmployeeNumber && exception.getStatus() == HttpStatus.CONFLICT) {
                    lastConflict = exception;
                    continue;
                }
                throw exception;
            }

            try {
                ScimModels.ScimUserResponse scim =
                        scimClient.create(toScimRequest(keycloakUserId, resolvedRequest, username));
                return new AdminUserResponses.ProvisionedUserResponse(
                        keycloakUserId,
                        scim == null ? null : scim.id(),
                        username,
                        resolvedRequest.email(),
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

        throw lastConflict == null
                ? new ProvisioningException(
                        HttpStatus.CONFLICT,
                        "EMPLOYEE_NUMBER_AUTO_ISSUE_FAILED",
                        "자동 사번 발급에 실패했습니다."
                )
                : lastConflict;
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
        KeycloakModels.UserRepresentation current = keycloakAdminClient.getUser(keycloakUserId);
        keycloakAdminClient.updateUser(
                keycloakUserId,
                toKeycloakRequest(request, username, current.requiredActions())
        );

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
        List<AdminUserResponses.KeycloakUserSummary> users = new ArrayList<>();
        boolean scimAvailable = true;
        for (KeycloakModels.UserRepresentation user : keycloakAdminClient.searchUsers(search, first, max)) {
            ScimModels.ScimUserResponse scim = null;
            if (scimAvailable && user != null && StringUtils.hasText(user.id())) {
                try {
                    scim = scimClient.findByExternalId(user.id()).orElse(null);
                } catch (ProvisioningException exception) {
                    scimAvailable = false;
                }
            }
            users.add(toKeycloakSummary(user, scim));
        }
        return users;
    }

    public AdminUserResponses.AdminUserDetailResponse get(String keycloakUserId) {
        KeycloakModels.UserRepresentation keycloak = keycloakAdminClient.getUser(keycloakUserId);
        AdminUserResponses.ScimUserSummary scim = scimClient.findByExternalId(keycloakUserId)
                .map(this::toScimSummary)
                .orElse(null);

        return new AdminUserResponses.AdminUserDetailResponse(
                toKeycloakSummary(keycloak, null),
                scim,
                lockStatus(keycloakUserId)
        );
    }

    public AdminUserResponses.NextEmployeeNumberResponse nextEmployeeNumber(
            ProvisioningEnums.UserRole role
    ) {
        String prefix = employeeNumberPrefix(role);
        return new AdminUserResponses.NextEmployeeNumberResponse(prefix, nextEmployeeNumberValue(prefix));
    }

    public AdminUserResponses.PasswordLockPolicyResponse passwordLockPolicy() {
        return toPasswordLockPolicyResponse(keycloakAdminClient.getPasswordLockPolicy());
    }

    public AdminUserResponses.PasswordLockPolicyResponse updatePasswordLockPolicy(Boolean enabled) {
        return toPasswordLockPolicyResponse(
                keycloakAdminClient.updatePasswordLockPolicy(enabled == null || enabled)
        );
    }

    public AdminUserResponses.ProvisionedUserResponse unlock(String keycloakUserId) {
        KeycloakModels.UserRepresentation keycloak = keycloakAdminClient.getUser(keycloakUserId);
        keycloakAdminClient.unlockUser(keycloakUserId);

        return new AdminUserResponses.ProvisionedUserResponse(
                keycloakUserId,
                null,
                keycloak == null ? null : keycloak.username(),
                keycloak == null ? null : keycloak.email(),
                "UNLOCKED"
        );
    }

    public AdminUserResponses.UserMaintenanceResponse applyCurrentSettingsToAllUsers(
            Boolean passwordLockEnabled
    ) {
        if (passwordLockEnabled != null) {
            keycloakAdminClient.updatePasswordLockPolicy(passwordLockEnabled);
        }

        int requested = 0;
        int updated = 0;
        int unchanged = 0;
        List<String> failedUsers = new ArrayList<>();

        int first = 0;
        int pageSize = 100;
        while (true) {
            List<KeycloakModels.UserRepresentation> users =
                    keycloakAdminClient.searchUsers(null, first, pageSize);
            if (users.isEmpty()) {
                break;
            }

            for (KeycloakModels.UserRepresentation user : users) {
                requested++;
                if (user == null || !StringUtils.hasText(user.id())) {
                    failedUsers.add("unknown: Keycloak user id가 없습니다.");
                    continue;
                }

                List<String> requiredActions = requiredActionsWithDefaults(user.requiredActions());
                if (requiredActions.equals(safeRequiredActions(user.requiredActions()))) {
                    unchanged++;
                    continue;
                }

                try {
                    keycloakAdminClient.updateUserProfile(
                            user.id(),
                            keycloakMaintenanceRequest(user, requiredActions)
                    );
                    updated++;
                } catch (ProvisioningException exception) {
                    failedUsers.add(displayFailureUser(user) + ": " + exception.getMessage());
                }
            }

            if (users.size() < pageSize) {
                break;
            }
            first += pageSize;
        }

        return new AdminUserResponses.UserMaintenanceResponse(
                requested,
                updated,
                unchanged,
                failedUsers,
                failedUsers.isEmpty() ? "APPLIED" : "PARTIAL"
        );
    }

    private AdminUserResponses.PasswordLockPolicyResponse toPasswordLockPolicyResponse(
            KeycloakModels.PasswordLockPolicy policy
    ) {
        return new AdminUserResponses.PasswordLockPolicyResponse(
                policy.enabled(),
                policy.failureFactor()
        );
    }

    private AdminUserResponses.UserLockStatusResponse lockStatus(String keycloakUserId) {
        try {
            KeycloakModels.UserLockStatus status = keycloakAdminClient.getUserLockStatus(keycloakUserId);
            return new AdminUserResponses.UserLockStatusResponse(status.locked(), status.numFailures());
        } catch (ProvisioningException exception) {
            return null;
        }
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

    private AdminUserRequests.CreateUserRequest withResolvedEmployeeNumber(
            AdminUserRequests.CreateUserRequest request
    ) {
        if (StringUtils.hasText(request.employeeNumber())) {
            return withEmployeeNumber(request, request.employeeNumber().trim());
        }

        return withEmployeeNumber(request, loginId(request.employeeNumber()));
    }

    private AdminUserRequests.CreateUserRequest withEmployeeNumber(
            AdminUserRequests.CreateUserRequest request,
            String employeeNumber
    ) {
        return new AdminUserRequests.CreateUserRequest(
                request.email(),
                request.displayName(),
                request.password(),
                request.temporaryPassword(),
                request.enabled(),
                request.emailVerified(),
                employeeNumber,
                request.autoEmployeeNumber(),
                request.position(),
                request.role(),
                request.tenancyType(),
                request.tenancyName(),
                request.sourceActive(),
                request.requireTotp(),
                request.attributes()
        );
    }

    private String nextEmployeeNumberValue(String prefix) {
        int first = 0;
        int pageSize = 100;
        int maxNumber = 0;
        int maxWidth = 3;

        while (true) {
            List<KeycloakModels.UserRepresentation> users =
                    keycloakAdminClient.searchUsers(null, first, pageSize);
            if (users.isEmpty()) {
                break;
            }

            for (KeycloakModels.UserRepresentation user : users) {
                NumberToken token = employeeNumberToken(prefix, user);
                if (token == null) {
                    continue;
                }
                if (token.number() > maxNumber) {
                    maxNumber = token.number();
                    maxWidth = token.width();
                } else if (token.number() == maxNumber) {
                    maxWidth = Math.max(maxWidth, token.width());
                }
            }

            if (users.size() < pageSize) {
                break;
            }
            first += pageSize;
        }

        return prefix + String.format("%0" + Math.max(3, maxWidth) + "d", maxNumber + 1);
    }

    private NumberToken employeeNumberToken(
            String prefix,
            KeycloakModels.UserRepresentation user
    ) {
        if (user == null) {
            return null;
        }

        List<String> candidates = new ArrayList<>();
        candidates.add(user.username());
        addAttributeValues(candidates, user.attributes(), "employee_number");
        addAttributeValues(candidates, user.attributes(), "employeeNumber");

        Pattern pattern = Pattern.compile("^" + Pattern.quote(prefix) + "(\\d+)$", Pattern.CASE_INSENSITIVE);
        NumberToken best = null;
        for (String candidate : candidates) {
            if (!StringUtils.hasText(candidate)) {
                continue;
            }
            Matcher matcher = pattern.matcher(candidate.trim());
            if (!matcher.matches()) {
                continue;
            }

            String digits = matcher.group(1);
            NumberToken token = new NumberToken(Integer.parseInt(digits), digits.length());
            if (best == null || token.number() > best.number()) {
                best = token;
            }
        }
        return best;
    }

    private void addAttributeValues(
            List<String> candidates,
            Map<String, List<String>> attributes,
            String key
    ) {
        if (attributes == null || attributes.get(key) == null) {
            return;
        }
        candidates.addAll(attributes.get(key));
    }

    private String employeeNumberPrefix(ProvisioningEnums.UserRole role) {
        return switch (role) {
            case HQ_MANAGER, HQ_STAFF -> "HQ";
            case BRANCH_MANAGER, BRANCH_STAFF -> "BR";
            case ADMIN -> throw new ProvisioningException(
                    HttpStatus.BAD_REQUEST,
                    "EMPLOYEE_NUMBER_AUTO_ISSUE_UNSUPPORTED_ROLE",
                    "전체 관리자는 자동 사번 발급 대상이 아닙니다."
            );
        };
    }

    private record NumberToken(int number, int width) {
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
                request.displayName(),
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
                credentials(request.password(), request.temporaryPassword()),
                requiredActionsForCreate(request.requireTotp())
        );
    }

    private KeycloakModels.UserRepresentation toKeycloakRequest(
            AdminUserRequests.UpdateUserRequest request,
            String username,
            List<String> currentActions
    ) {
        return new KeycloakModels.UserRepresentation(
                null,
                username,
                request.email(),
                request.displayName(),
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
                credentials(request.password(), request.temporaryPassword()),
                requiredActionsForUpdate(currentActions, request.requireTotp())
        );
    }

    private List<String> requiredOtpAction() {
        return List.of(CONFIGURE_TOTP_REQUIRED_ACTION);
    }

    private List<String> requiredActionsForCreate(Boolean requireTotp) {
        return Boolean.FALSE.equals(requireTotp) ? List.of() : requiredOtpAction();
    }

    private List<String> requiredActionsForUpdate(
            List<String> currentActions,
            Boolean requireTotp
    ) {
        Set<String> actions = new LinkedHashSet<>(safeRequiredActions(currentActions));
        if (Boolean.FALSE.equals(requireTotp)) {
            actions.remove(CONFIGURE_TOTP_REQUIRED_ACTION);
        } else {
            actions.add(CONFIGURE_TOTP_REQUIRED_ACTION);
        }
        return new ArrayList<>(actions);
    }

    private List<String> requiredActionsWithDefaults(List<String> currentActions) {
        Set<String> actions = new LinkedHashSet<>(safeRequiredActions(currentActions));
        actions.addAll(requiredOtpAction());
        return new ArrayList<>(actions);
    }

    private List<String> safeRequiredActions(List<String> currentActions) {
        return currentActions == null ? List.of() : currentActions;
    }

    private KeycloakModels.UserRepresentation keycloakMaintenanceRequest(
            KeycloakModels.UserRepresentation user,
            List<String> requiredActions
    ) {
        return new KeycloakModels.UserRepresentation(
                user.id(),
                user.username(),
                user.email(),
                user.firstName(),
                user.lastName(),
                user.enabled(),
                user.emailVerified(),
                user.attributes(),
                null,
                requiredActions
        );
    }

    private String displayFailureUser(KeycloakModels.UserRepresentation user) {
        if (StringUtils.hasText(user.username())) {
            return user.username();
        }
        return StringUtils.hasText(user.id()) ? user.id() : "unknown";
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
            KeycloakModels.UserRepresentation user,
            ScimModels.ScimUserResponse scim
    ) {
        if (user == null) {
            return null;
        }
        Map<String, List<String>> attributes = new LinkedHashMap<>();
        if (user.attributes() != null) {
            user.attributes().forEach((key, value) ->
                    attributes.put(key, value == null ? List.of() : new ArrayList<>(value))
            );
        }

        String scimDisplayName = scim == null ? null : scim.displayName();
        String scimEmployeeNumber = scim == null || scim.enterprise() == null
                ? null
                : scim.enterprise().employeeNumber();
        String scimRole = scim == null || scim.erp() == null ? null : scim.erp().role();
        String scimTenancyType = scim == null || scim.erp() == null ? null : scim.erp().tenancyType();
        String scimTenancyName = scim == null || scim.erp() == null ? null : scim.erp().tenancyName();

        putAttribute(attributes, "displayName", scimDisplayName);
        putAttribute(attributes, "name", scimDisplayName);
        putAttribute(attributes, "employee_number", scimEmployeeNumber);
        putAttribute(attributes, "employeeNumber", scimEmployeeNumber);
        putAttribute(attributes, "position", scim == null ? null : scim.title());
        putAttribute(attributes, "role", scimRole);
        putAttribute(attributes, "erpRole", scimRole);
        putAttribute(attributes, "tenancy_type", scimTenancyType);
        putAttribute(attributes, "tenancyType", scimTenancyType);
        putAttribute(attributes, "tenancy_name", scimTenancyName);
        putAttribute(attributes, "tenancyName", scimTenancyName);

        return new AdminUserResponses.KeycloakUserSummary(
                user.id(),
                user.username(),
                user.email(),
                StringUtils.hasText(scimDisplayName) ? scimDisplayName : user.firstName(),
                user.lastName(),
                user.enabled(),
                user.emailVerified(),
                attributes,
                safeRequiredActions(user.requiredActions())
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
