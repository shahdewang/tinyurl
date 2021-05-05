package com.bufferstack.tinyurl.models;

import java.time.Instant;

public class TinyUrlMapping {

    private String code;
    private String fullUrl;
    private Instant createdAt;

    public TinyUrlMapping(String code, String fullUrl, Instant createdAt) {
        this.code = code;
        this.fullUrl = fullUrl;
        this.createdAt = createdAt;
    }

    public String getCode() {
        return code;
    }

    public String getFullUrl() {
        return fullUrl;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
