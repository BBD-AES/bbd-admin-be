package com.hd.hdp.provisioning.keycloak;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.DeleteExchange;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;
import org.springframework.web.service.annotation.PutExchange;

@HttpExchange("/admin/realms/{realm}/users")
public interface KeycloakAdminHttpService {

    @PostExchange(contentType = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> createUser(
            @PathVariable String realm,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestBody KeycloakModels.UserRepresentation request
    );

    @GetExchange("/{userId}")
    KeycloakModels.UserRepresentation getUser(
            @PathVariable String realm,
            @PathVariable String userId,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization
    );

    @GetExchange
    KeycloakModels.UserRepresentation[] searchUsers(
            @PathVariable String realm,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestParam int first,
            @RequestParam int max
    );

    @GetExchange
    KeycloakModels.UserRepresentation[] searchUsers(
            @PathVariable String realm,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestParam String search,
            @RequestParam int first,
            @RequestParam int max
    );

    @PutExchange(value = "/{userId}", contentType = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> updateUser(
            @PathVariable String realm,
            @PathVariable String userId,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestBody KeycloakModels.UserRepresentation request
    );

    @PutExchange(value = "/{userId}/reset-password", contentType = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> resetPassword(
            @PathVariable String realm,
            @PathVariable String userId,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestBody KeycloakModels.CredentialRepresentation credential
    );

    @DeleteExchange("/{userId}")
    ResponseEntity<Void> deleteUser(
            @PathVariable String realm,
            @PathVariable String userId,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization
    );
}
