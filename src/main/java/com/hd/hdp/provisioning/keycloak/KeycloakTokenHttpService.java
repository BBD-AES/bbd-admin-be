package com.hd.hdp.provisioning.keycloak;

import org.springframework.http.MediaType;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

@HttpExchange("/realms/{realm}/protocol/openid-connect")
public interface KeycloakTokenHttpService {

    @PostExchange(value = "/token", contentType = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    KeycloakModels.TokenResponse requestToken(
            @PathVariable String realm,
            @RequestBody MultiValueMap<String, String> form
    );
}
