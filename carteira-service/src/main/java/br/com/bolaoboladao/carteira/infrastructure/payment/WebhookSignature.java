package br.com.bolaoboladao.carteira.infrastructure.payment;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;

@ApplicationScoped
public class WebhookSignature {
    @ConfigProperty(name = "payment.webhook.secret", defaultValue = "") String configuredSecret;
    @ConfigProperty(name = "payment.webhook.secret-file", defaultValue = "") String secretFile;
    @ConfigProperty(name = "payment.webhook.tolerance-seconds", defaultValue = "300") long toleranceSeconds;

    private byte[] secret;

    @PostConstruct
    void initialize() {
        String value = configuredSecret == null ? "" : configuredSecret.trim();
        if (secretFile != null && !secretFile.isBlank()) {
            try {
                value = Files.readString(Path.of(secretFile)).trim();
            } catch (IOException exception) {
                throw new IllegalStateException("Could not read payment webhook secret", exception);
            }
        }
        if (value.isBlank()) throw new IllegalStateException("Payment webhook secret is not configured");
        secret = value.getBytes(StandardCharsets.UTF_8);
    }

    public boolean valid(String timestamp, String rawBody, String suppliedSignature) {
        try {
            long seconds = Long.parseLong(timestamp);
            if (Math.abs(Instant.now().getEpochSecond() - seconds) > toleranceSeconds) return false;
            byte[] expected = sign(timestamp, rawBody).getBytes(StandardCharsets.US_ASCII);
            byte[] supplied = suppliedSignature == null
                    ? new byte[0]
                    : suppliedSignature.getBytes(StandardCharsets.US_ASCII);
            return MessageDigest.isEqual(expected, supplied);
        } catch (RuntimeException exception) {
            return false;
        }
    }

    public String sign(String timestamp, String rawBody) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            byte[] digest = mac.doFinal((timestamp + "." + rawBody).getBytes(StandardCharsets.UTF_8));
            return "v1=" + HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not calculate webhook signature", exception);
        }
    }
}
