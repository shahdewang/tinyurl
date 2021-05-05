package com.bufferstack.tinyurl.zookeeper;

public interface IdentifierStream<T> {

    T next();

    boolean ready();
}
