package com.thirdexploration.promengine.core.domain;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

public class AtomicIntegerSerializer extends JsonSerializer<AtomicInteger> {

    @Override
    public void serialize(AtomicInteger value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        if (value == null) {
            gen.writeNull();
        } else {
            gen.writeNumber(value.get());
        }
    }
}