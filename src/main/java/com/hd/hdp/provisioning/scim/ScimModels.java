package com.hd.hdp.provisioning.scim;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public final class ScimModels {

    public static final String MEDIA_TYPE = "application/scim+json";
    public static final String CORE_USER_SCHEMA = "urn:ietf:params:scim:schemas:core:2.0:User";
    public static final String ENTERPRISE_USER_SCHEMA =
            "urn:ietf:params:scim:schemas:extension:enterprise:2.0:User";
    public static final String ERP_USER_SCHEMA =
            "urn:bbd:params:scim:schemas:extension:erp:2.0:User";

    private ScimModels() {
    }

    public record ScimUserRequest(
            List<String> schemas,
            String externalId,
            String userName,
            String displayName,
            ScimName name,
            String title,
            List<ScimEmail> emails,
            List<ScimRole> roles,
            Boolean active,
            @JsonProperty(ENTERPRISE_USER_SCHEMA)
            EnterpriseExtension enterprise,
            @JsonProperty(ERP_USER_SCHEMA)
            ErpExtension erp
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ScimUserResponse(
            List<String> schemas,
            String id,
            String externalId,
            String userName,
            String displayName,
            ScimName name,
            String title,
            List<ScimEmail> emails,
            List<ScimRole> roles,
            Boolean active,
            @JsonProperty(ENTERPRISE_USER_SCHEMA)
            EnterpriseExtension enterprise,
            @JsonProperty(ERP_USER_SCHEMA)
            ErpExtension erp
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ScimListResponse(
            Integer totalResults,
            Integer startIndex,
            Integer itemsPerPage,
            @JsonProperty("Resources")
            List<ScimUserResponse> resources
    ) {
        public List<ScimUserResponse> safeResources() {
            return resources == null ? List.of() : resources;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ScimName(String formatted) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ScimEmail(String value, String type, Boolean primary) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ScimRole(String value, String display, Boolean primary) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EnterpriseExtension(
            String employeeNumber,
            String organization,
            String department
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ErpExtension(
            String role,
            String tenancyType,
            String tenancyName
    ) {
    }
}
