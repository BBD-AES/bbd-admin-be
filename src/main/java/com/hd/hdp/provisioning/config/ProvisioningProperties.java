package com.hd.hdp.provisioning.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "hdp.provisioning")
public class ProvisioningProperties {

    private final Security security = new Security();
    private final Keycloak keycloak = new Keycloak();
    private final Scim scim = new Scim();
    private final Compensation compensation = new Compensation();

    public Security getSecurity() {
        return security;
    }

    public Keycloak getKeycloak() {
        return keycloak;
    }

    public Scim getScim() {
        return scim;
    }

    public Compensation getCompensation() {
        return compensation;
    }

    public static class Security {
        private boolean enabled = true;
        private boolean enforceAdminRole;
        private String requiredAdminRole = "admin";
        private String postLoginRedirectUri = "http://localhost:5174";
        private String postLogoutRedirectUri = "http://localhost:5174/login";
        private List<String> allowedOrigins = new ArrayList<>(List.of("http://localhost:5174"));

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isEnforceAdminRole() {
            return enforceAdminRole;
        }

        public void setEnforceAdminRole(boolean enforceAdminRole) {
            this.enforceAdminRole = enforceAdminRole;
        }

        public String getRequiredAdminRole() {
            return requiredAdminRole;
        }

        public void setRequiredAdminRole(String requiredAdminRole) {
            this.requiredAdminRole = requiredAdminRole;
        }

        public String getPostLoginRedirectUri() {
            return postLoginRedirectUri;
        }

        public void setPostLoginRedirectUri(String postLoginRedirectUri) {
            this.postLoginRedirectUri = postLoginRedirectUri;
        }

        public String getPostLogoutRedirectUri() {
            return postLogoutRedirectUri;
        }

        public void setPostLogoutRedirectUri(String postLogoutRedirectUri) {
            this.postLogoutRedirectUri = postLogoutRedirectUri;
        }

        public List<String> getAllowedOrigins() {
            return allowedOrigins;
        }

        public void setAllowedOrigins(List<String> allowedOrigins) {
            this.allowedOrigins = allowedOrigins;
        }
    }

    public static class Keycloak {
        private String serverUrl = "http://localhost:8080";
        private String realm = "bbd";
        private String adminClientId = "user-admin-console-admin";
        private String adminClientSecret = "";
        private long tokenCacheSkewSeconds = 30;

        public String getServerUrl() {
            return serverUrl;
        }

        public void setServerUrl(String serverUrl) {
            this.serverUrl = serverUrl;
        }

        public String getRealm() {
            return realm;
        }

        public void setRealm(String realm) {
            this.realm = realm;
        }

        public String getAdminClientId() {
            return adminClientId;
        }

        public void setAdminClientId(String adminClientId) {
            this.adminClientId = adminClientId;
        }

        public String getAdminClientSecret() {
            return adminClientSecret;
        }

        public void setAdminClientSecret(String adminClientSecret) {
            this.adminClientSecret = adminClientSecret;
        }

        public long getTokenCacheSkewSeconds() {
            return tokenCacheSkewSeconds;
        }

        public void setTokenCacheSkewSeconds(long tokenCacheSkewSeconds) {
            this.tokenCacheSkewSeconds = tokenCacheSkewSeconds;
        }
    }

    public static class Scim {
        private String baseUrl = "http://localhost:8080/user";
        private AuthMode authMode = AuthMode.NONE;
        private String bearerToken = "";
        private String basicUsername = "";
        private String basicPassword = "";
        private String apiKeyHeader = "X-API-Key";
        private String apiKey = "";
        private final Mtls mtls = new Mtls();

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public AuthMode getAuthMode() {
            return authMode;
        }

        public void setAuthMode(AuthMode authMode) {
            this.authMode = authMode;
        }

        public String getBearerToken() {
            return bearerToken;
        }

        public void setBearerToken(String bearerToken) {
            this.bearerToken = bearerToken;
        }

        public String getBasicUsername() {
            return basicUsername;
        }

        public void setBasicUsername(String basicUsername) {
            this.basicUsername = basicUsername;
        }

        public String getBasicPassword() {
            return basicPassword;
        }

        public void setBasicPassword(String basicPassword) {
            this.basicPassword = basicPassword;
        }

        public String getApiKeyHeader() {
            return apiKeyHeader;
        }

        public void setApiKeyHeader(String apiKeyHeader) {
            this.apiKeyHeader = apiKeyHeader;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public Mtls getMtls() {
            return mtls;
        }
    }

    public static class Mtls {
        private String keyStore = "";
        private String keyStorePassword = "";
        private String keyStoreType = "PKCS12";
        private String trustStore = "";
        private String trustStorePassword = "";
        private String trustStoreType = "PKCS12";

        public String getKeyStore() {
            return keyStore;
        }

        public void setKeyStore(String keyStore) {
            this.keyStore = keyStore;
        }

        public String getKeyStorePassword() {
            return keyStorePassword;
        }

        public void setKeyStorePassword(String keyStorePassword) {
            this.keyStorePassword = keyStorePassword;
        }

        public String getKeyStoreType() {
            return keyStoreType;
        }

        public void setKeyStoreType(String keyStoreType) {
            this.keyStoreType = keyStoreType;
        }

        public String getTrustStore() {
            return trustStore;
        }

        public void setTrustStore(String trustStore) {
            this.trustStore = trustStore;
        }

        public String getTrustStorePassword() {
            return trustStorePassword;
        }

        public void setTrustStorePassword(String trustStorePassword) {
            this.trustStorePassword = trustStorePassword;
        }

        public String getTrustStoreType() {
            return trustStoreType;
        }

        public void setTrustStoreType(String trustStoreType) {
            this.trustStoreType = trustStoreType;
        }
    }

    public static class Compensation {
        private CreateFailureMode createFailureMode = CreateFailureMode.DELETE;

        public CreateFailureMode getCreateFailureMode() {
            return createFailureMode;
        }

        public void setCreateFailureMode(CreateFailureMode createFailureMode) {
            this.createFailureMode = createFailureMode;
        }
    }

    public enum AuthMode {
        NONE,
        BEARER,
        BASIC,
        API_KEY,
        MTLS
    }

    public enum CreateFailureMode {
        NONE,
        DISABLE,
        DELETE
    }
}
