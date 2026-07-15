package br.com.bolaoboladao.carteira.presentation.rest;

import br.com.bolaoboladao.carteira.application.ApiException;
import br.com.bolaoboladao.carteira.application.ProcessDepositUseCase;
import br.com.bolaoboladao.carteira.infrastructure.payment.ProviderCharge;
import br.com.bolaoboladao.carteira.infrastructure.payment.WebhookSignature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.UUID;

@Path("/wallet/webhooks/boladao-pay")
@Produces(MediaType.APPLICATION_JSON)
public class PaymentWebhookResource {
    @Inject WebhookSignature signature;
    @Inject ObjectMapper objectMapper;
    @Inject ProcessDepositUseCase processDeposit;

    @POST
    public Uni<Response> receive(String rawBody,
                                 @HeaderParam("X-Boladao-Pay-Event-Id") String eventIdHeader,
                                 @HeaderParam("X-Boladao-Pay-Timestamp") String timestamp,
                                 @HeaderParam("X-Boladao-Pay-Signature") String suppliedSignature) {
        if (!signature.valid(timestamp, rawBody, suppliedSignature)) {
            return Uni.createFrom().item(Response.status(401).build());
        }

        try {
            JsonNode payload = objectMapper.readTree(rawBody);
            UUID eventId = UUID.fromString(payload.required("eventId").asText());
            String eventType = payload.required("eventType").asText();
            JsonNode data = payload.required("data");
            if (!eventId.toString().equals(eventIdHeader)) {
                throw new ApiException(409, "WEBHOOK_EVENT_MISMATCH", "O identificador do evento não corresponde ao payload.");
            }
            String status = data.required("status").asText();
            if (!("CHARGE_" + status).equals(eventType)) {
                throw new ApiException(409, "WEBHOOK_STATUS_MISMATCH", "O tipo do evento não corresponde ao estado.");
            }
            ProviderCharge charge = new ProviderCharge(
                    UUID.fromString(data.required("chargeId").asText()),
                    UUID.fromString(data.required("merchantReference").asText()),
                    data.required("amountCents").asLong(), ProviderCharge.Status.from(status), null, null, null);
            return processDeposit.execute(eventId, eventType, charge)
                    .replaceWith(Response.noContent().build());
        } catch (ApiException exception) {
            return Uni.createFrom().failure(exception);
        } catch (Exception exception) {
            return Uni.createFrom().failure(new ApiException(400, "INVALID_WEBHOOK", "O webhook recebido é inválido."));
        }
    }
}
