package com.travelease.common.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DomainEventEnvelopeTest {

    private static final Instant AT = Instant.parse("2026-10-08T15:00:00Z");

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private DomainEventEnvelope read(String recipientsFragment) throws Exception {
        return objectMapper.readValue("""
                {"entityType":"TRAVEL_REQUEST","entityId":"2","action":"APPROVED",
                 "timestamp":"2026-10-08T15:00:00Z"%s}
                """.formatted(recipientsFragment), DomainEventEnvelope.class);
    }

    @Test
    @DisplayName("null recipients passed to the canonical constructor become an empty list")
    void canonicalConstructorNormalizesNull() {
        assertThat(new DomainEventEnvelope("T", "1", "A", AT, null).recipientIds()).isEmpty();
        assertThat(new DomainEventEnvelope("T", "1", "A", AT))
                .isEqualTo(new DomainEventEnvelope("T", "1", "A", AT, null));
    }

    @Test
    @DisplayName("JSON without recipientIds deserializes to an empty list")
    void missingFieldDeserializesToEmptyList() throws Exception {
        assertThat(read("").recipientIds()).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("JSON with explicit null recipientIds deserializes to an empty list")
    void explicitNullDeserializesToEmptyList() throws Exception {
        assertThat(read(",\"recipientIds\":null").recipientIds()).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("JSON with an empty or populated array keeps its contents")
    void arraysDeserializeUnchanged() throws Exception {
        assertThat(read(",\"recipientIds\":[]").recipientIds()).isEmpty();
        assertThat(read(",\"recipientIds\":[7,8]").recipientIds()).containsExactly(7L, 8L);
    }

    @Test
    @DisplayName("serialized envelopes always carry an array, never null")
    void serializesEmptyArray() throws Exception {
        String json = objectMapper.writeValueAsString(new DomainEventEnvelope("T", "1", "A", AT, null));
        assertThat(json).contains("\"recipientIds\":[]");
    }
}
