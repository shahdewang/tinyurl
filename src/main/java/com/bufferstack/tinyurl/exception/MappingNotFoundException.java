package com.bufferstack.tinyurl.exception;

public class MappingNotFoundException extends RuntimeException {

    public MappingNotFoundException(String code) {
        super("URL mapping not found for tiny code: " + code);
    }
}
