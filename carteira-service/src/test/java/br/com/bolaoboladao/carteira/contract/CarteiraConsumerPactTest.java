package br.com.bolaoboladao.carteira.contract;

import au.com.dius.pact.consumer.dsl.PactBuilder;
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt;
import au.com.dius.pact.consumer.junit5.PactTestFor;
import au.com.dius.pact.consumer.junit5.ProviderType;
import au.com.dius.pact.core.model.annotations.Pact;
import au.com.dius.pact.core.model.V4Interaction;
import au.com.dius.pact.core.model.V4Pact;
import br.com.bolaoboladao.carteira.presentation.messaging.dto.BetEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@ExtendWith(PactConsumerTestExt.class)
@PactTestFor(providerName = "apostas-service", providerType = ProviderType.ASYNCH)
public class CarteiraConsumerPactTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Pact(consumer = "carteira-service")
    public V4Pact createBetCreatedPact(PactBuilder builder) {
        return builder.usingLegacyMessageDsl()
                .expectsToReceive("a BET_CREATED event")
                .withMetadata(java.util.Map.of("contentType", "application/json"))
                .withContent("{\"eventId\": \"123e4567-e89b-12d3-a456-426614174000\", \"eventType\": \"BET_CREATED\", \"betId\": \"123e4567-e89b-12d3-a456-426614174001\", \"userId\": \"123e4567-e89b-12d3-a456-426614174002\", \"amount\": 50.00}")
                .toPact()
                .asV4Pact()
                .get();
    }

    @Test
    @PactTestFor(pactMethod = "createBetCreatedPact")
    public void testBetCreatedEvent(List<V4Interaction.AsynchronousMessage> messages) throws Exception {
        V4Interaction.AsynchronousMessage message = messages.get(0);
        assertNotNull(message);

        String json = message.contentsAsString();
        BetEvent event = objectMapper.readValue(json, BetEvent.class);

        assertEquals("BET_CREATED", event.eventType());
        assertEquals("123e4567-e89b-12d3-a456-426614174001", event.betId().toString());
        assertEquals("123e4567-e89b-12d3-a456-426614174002", event.userId().toString());
        assertEquals(new java.math.BigDecimal("50.00"), event.amount());
    }
}
