package com.bufferstack.tinyurl.generator;

public interface IdentifierStream<T> {

    T next();

    boolean ready();
}
