package io.confluent.developer.commonSample;

import io.confluent.developer.avro.AuthorValue;

public class AuthorMessage {
    private String key;
    private AuthorValue value;

    public AuthorMessage(String key, AuthorValue value) {
        this.key = key;
        this.value = value;
    }

    public String getKey() {
        return key;
    }

    public AuthorValue getValue() {
        return value;
    }
}