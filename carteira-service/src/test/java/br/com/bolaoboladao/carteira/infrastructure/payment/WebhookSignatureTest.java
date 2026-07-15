package br.com.bolaoboladao.carteira.infrastructure.payment;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebhookSignatureTest {
    @Test
    void validatesRawBodyAndRejectsTamperingAndExpiredTimestamp() {
        var signature = new WebhookSignature();
        signature.configuredSecret = "test-secret";
        signature.secretFile = "";
        signature.toleranceSeconds = 300;
        signature.initialize();

        String timestamp = Long.toString(Instant.now().getEpochSecond());
        String body = "{\"eventId\":\"event-1\",\"amountCents\":5000}";
        String signed = signature.sign(timestamp, body);

        assertTrue(signature.valid(timestamp, body, signed));
        assertFalse(signature.valid(timestamp, body + " ", signed));
        assertFalse(signature.valid(Long.toString(Instant.now().minusSeconds(301).getEpochSecond()), body, signed));
    }
}
