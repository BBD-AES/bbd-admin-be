package com.hd.hdp.provisioning.config;

import com.hd.hdp.provisioning.keycloak.KeycloakAdminHttpService;
import com.hd.hdp.provisioning.keycloak.KeycloakTokenHttpService;
import com.hd.hdp.provisioning.scim.ScimHttpService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;

@Configuration
public class HttpServiceConfig {

    @Bean
    KeycloakAdminHttpService keycloakAdminHttpService(ProvisioningProperties properties) {
        return httpService(keycloakRestClient(properties), KeycloakAdminHttpService.class);
    }

    @Bean
    KeycloakTokenHttpService keycloakTokenHttpService(ProvisioningProperties properties) {
        return httpService(keycloakRestClient(properties), KeycloakTokenHttpService.class);
    }

    @Bean
    ScimHttpService scimHttpService(ProvisioningProperties properties) {
        return httpService(scimRestClient(properties), ScimHttpService.class);
    }

    private RestClient keycloakRestClient(ProvisioningProperties properties) {
        return RestClient.builder()
                .baseUrl(stripTrailingSlash(properties.getKeycloak().getServerUrl()))
                .build();
    }

    private RestClient scimRestClient(ProvisioningProperties properties) {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(stripTrailingSlash(properties.getScim().getBaseUrl()));

        if (properties.getScim().getAuthMode() == ProvisioningProperties.AuthMode.MTLS) {
            builder.requestFactory(new JdkClientHttpRequestFactory(
                    HttpClient.newBuilder()
                            .sslContext(scimSslContext(properties.getScim().getMtls()))
                            .build()
            ));
        }

        return builder.build();
    }

    private <T> T httpService(RestClient restClient, Class<T> serviceType) {
        HttpServiceProxyFactory factory = HttpServiceProxyFactory
                .builderFor(RestClientAdapter.create(restClient))
                .build();
        return factory.createClient(serviceType);
    }

    private SSLContext scimSslContext(ProvisioningProperties.Mtls mtls) {
        try {
            KeyManagerFactory keyManagerFactory = null;
            TrustManagerFactory trustManagerFactory = null;

            if (StringUtils.hasText(mtls.getKeyStore())) {
                KeyStore keyStore = loadStore(
                        mtls.getKeyStore(),
                        mtls.getKeyStoreType(),
                        mtls.getKeyStorePassword()
                );
                keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
                keyManagerFactory.init(keyStore, mtls.getKeyStorePassword().toCharArray());
            }

            if (StringUtils.hasText(mtls.getTrustStore())) {
                KeyStore trustStore = loadStore(
                        mtls.getTrustStore(),
                        mtls.getTrustStoreType(),
                        mtls.getTrustStorePassword()
                );
                trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                trustManagerFactory.init(trustStore);
            }

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(
                    keyManagerFactory == null ? null : keyManagerFactory.getKeyManagers(),
                    trustManagerFactory == null ? null : trustManagerFactory.getTrustManagers(),
                    null
            );
            return sslContext;
        } catch (Exception exception) {
            throw new IllegalStateException("SCIM mTLS 설정을 초기화하지 못했습니다.", exception);
        }
    }

    private KeyStore loadStore(String location, String type, String password) throws Exception {
        KeyStore store = KeyStore.getInstance(type);
        try (InputStream inputStream = Files.newInputStream(Path.of(location))) {
            store.load(inputStream, password.toCharArray());
        }
        return store;
    }

    private String stripTrailingSlash(String value) {
        if (value == null || value.isBlank() || value.length() == 1) {
            return value;
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
