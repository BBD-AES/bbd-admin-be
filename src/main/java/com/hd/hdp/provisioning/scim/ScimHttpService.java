package com.hd.hdp.provisioning.scim;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.DeleteExchange;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;
import org.springframework.web.service.annotation.PutExchange;

import java.util.Map;

@HttpExchange(
        url = "/scim/v2/Users",
        accept = {ScimModels.MEDIA_TYPE, MediaType.APPLICATION_JSON_VALUE}
)
public interface ScimHttpService {

    @PostExchange(contentType = ScimModels.MEDIA_TYPE)
    ScimModels.ScimUserResponse create(
            @RequestHeader Map<String, String> headers,
            @RequestBody ScimModels.ScimUserRequest request
    );

    @GetExchange
    ScimModels.ScimListResponse search(
            @RequestHeader Map<String, String> headers,
            @RequestParam String filter,
            @RequestParam int startIndex,
            @RequestParam int count
    );

    @PutExchange(value = "/{userId}", contentType = ScimModels.MEDIA_TYPE)
    ScimModels.ScimUserResponse update(
            @RequestHeader Map<String, String> headers,
            @PathVariable String userId,
            @RequestBody ScimModels.ScimUserRequest request
    );

    @DeleteExchange("/{userId}")
    void delete(
            @RequestHeader Map<String, String> headers,
            @PathVariable String userId
    );
}
