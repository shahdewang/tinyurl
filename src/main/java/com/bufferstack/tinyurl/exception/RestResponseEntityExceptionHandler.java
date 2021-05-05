package com.bufferstack.tinyurl.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@ControllerAdvice
public class RestResponseEntityExceptionHandler extends ResponseEntityExceptionHandler {

    private final ObjectMapper objectMapper;

    public RestResponseEntityExceptionHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @ExceptionHandler(value = { MappingNotFoundException.class })
    protected ResponseEntity<Object> handleConflict(MappingNotFoundException ex, WebRequest request) {
        ObjectNode errorPayload = objectMapper.createObjectNode().put("message", ex.getMessage());
        return handleExceptionInternal(ex, errorPayload, new HttpHeaders(), HttpStatus.NOT_FOUND, request);
    }
}
