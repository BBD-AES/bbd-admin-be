package com.hd.hdp.provisioning.web;

import com.hd.hdp.provisioning.config.ProvisioningProperties;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class AuthTokenController {

    private final ProvisioningProperties properties;

    public AuthTokenController(ProvisioningProperties properties) {
        this.properties = properties;
    }

    @GetMapping(value = "/api/auth/token", produces = MediaType.TEXT_PLAIN_VALUE)
    String accessToken(
            @RegisteredOAuth2AuthorizedClient("keycloak") OAuth2AuthorizedClient authorizedClient
    ) {
        if (!properties.getSecurity().isExposeAccessToken()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        if (authorizedClient == null
                || authorizedClient.getAccessToken() == null
                || !StringUtils.hasText(authorizedClient.getAccessToken().getTokenValue())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No Keycloak access token in session.");
        }
        return authorizedClient.getAccessToken().getTokenValue();
    }
}
