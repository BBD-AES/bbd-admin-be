package com.hd.hdp.provisioning.error;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ProvisioningException.class)
    ResponseEntity<ApiErrorResponse> handleProvisioningException(
            ProvisioningException exception,
            HttpServletRequest request
    ) {
        return ResponseEntity
                .status(exception.getStatus())
                .body(ApiErrorResponse.of(
                        exception.getStatus(),
                        exception.getCode(),
                        exception.getMessage(),
                        request.getRequestURI(),
                        List.of()
                ));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiErrorResponse> handleValidationException(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        List<String> details = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(this::fieldError)
                .toList();

        return ResponseEntity
                .badRequest()
                .body(ApiErrorResponse.of(
                        HttpStatus.BAD_REQUEST,
                        "VALIDATION_ERROR",
                        "요청 값이 올바르지 않습니다.",
                        request.getRequestURI(),
                        details
                ));
    }

    private String fieldError(FieldError error) {
        return error.getField() + ": " + error.getDefaultMessage();
    }

    public record ApiErrorResponse(
            Instant timestamp,
            int status,
            String error,
            String code,
            String message,
            String path,
            List<String> details
    ) {
        static ApiErrorResponse of(
                HttpStatus status,
                String code,
                String message,
                String path,
                List<String> details
        ) {
            return new ApiErrorResponse(
                    Instant.now(),
                    status.value(),
                    status.getReasonPhrase(),
                    code,
                    message,
                    path,
                    details
            );
        }
    }
}
