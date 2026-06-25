package com.hd.hdp.provisioning.keycloak;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.DeleteExchange;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PutExchange;

import java.util.Map;

@HttpExchange("/admin/realms/{realm}")
public interface KeycloakRealmAdminHttpService {

    @GetExchange
    Map<String, Object> getRealm(
            @PathVariable String realm,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization
    );

    @PutExchange(contentType = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> updateRealm(
            @PathVariable String realm,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestBody Map<String, Object> request
    );

    @GetExchange("/attack-detection/brute-force/users/{userId}")
    Map<String, Object> getUserLockStatus(
            @PathVariable String realm,
            @PathVariable String userId,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization
    );

    @DeleteExchange("/attack-detection/brute-force/users/{userId}")
    ResponseEntity<Void> unlockUser(
            @PathVariable String realm,
            @PathVariable String userId,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization
    );
}
