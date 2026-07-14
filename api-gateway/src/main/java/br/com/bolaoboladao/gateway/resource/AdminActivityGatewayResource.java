package br.com.bolaoboladao.gateway.resource;

import br.com.bolaoboladao.gateway.client.BackendClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.jboss.logging.Logger;

import java.nio.charset.StandardCharsets;
import java.net.URLEncoder;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;

@Path("/api/admin/activity")
@RolesAllowed("ADMIN")
public class AdminActivityGatewayResource {
    private static final Logger LOG = Logger.getLogger(AdminActivityGatewayResource.class);

    @Inject BackendClient backendClient;
    @Inject JsonWebToken token;
    @Inject ObjectMapper objectMapper;
    @ConfigProperty(name = "partidas-service.url") String partidasServiceUrl;
    @ConfigProperty(name = "carteira-service.url") String carteiraServiceUrl;

    @GET
    public Uni<Response> activity(@QueryParam("cursor") String cursor,
                                  @QueryParam("size") @DefaultValue("20") int size,
                                  @QueryParam("type") String type,
                                  @Context HttpHeaders headers) {
        ActivityCursor offsets = decodeCursor(cursor);
        int safeSize = Math.max(1, Math.min(size, 50));
        String auth = headers.getHeaderString(HttpHeaders.AUTHORIZATION);
        String normalizedType = type == null ? "" : type.trim().toUpperCase();
        boolean isMatchType = "MATCH_CREATED".equals(normalizedType) || "MATCH_CANCELED".equals(normalizedType);
        String matchType = isMatchType ? "&type=" + normalizedType : "";
        String snapshot = "&until=" + URLEncoder.encode(offsets.until().toString(), StandardCharsets.UTF_8);
        var matches = backendClient.adminGet(partidasServiceUrl + "/admin/activity?offset="
                        + offsets.matches() + "&size=" + safeSize + snapshot + matchType,
                token.getSubject(), auth);
        var credits = backendClient.adminGet(carteiraServiceUrl + "/admin/activity?offset="
                        + offsets.credits() + "&size=" + safeSize + snapshot,
                token.getSubject(), auth);

        return Uni.combine().all().unis(matches, credits).asTuple().map(tuple -> {
            if (tuple.getItem1().statusCode() >= 400) return GatewayResponses.from(tuple.getItem1());
            if (tuple.getItem2().statusCode() >= 400) return GatewayResponses.from(tuple.getItem2());
            try {
                JsonNode matchPage = objectMapper.readTree(tuple.getItem1().bodyAsString());
                JsonNode creditPage = objectMapper.readTree(tuple.getItem2().bodyAsString());
                List<ActivityEntry> candidates = new ArrayList<>();
                if (normalizedType.isBlank() || isMatchType) {
                    matchPage.path("items").forEach(item -> candidates.add(
                            new ActivityEntry(matchActivity(item), ActivitySource.MATCH)));
                }
                if (normalizedType.isBlank() || "ADMIN_CREDIT".equals(normalizedType)) {
                    creditPage.path("items").forEach(item -> candidates.add(
                            new ActivityEntry(creditActivity(item), ActivitySource.CREDIT)));
                }
                candidates.sort(Comparator.comparing((ActivityEntry entry) -> occurredAt(entry.value())).reversed());
                List<ActivityEntry> selected = candidates.stream().limit(safeSize).toList();

                ObjectNode result = objectMapper.createObjectNode();
                ArrayNode array = result.putArray("items");
                selected.forEach(entry -> array.add(entry.value()));
                long matchTotal = normalizedType.isBlank() || isMatchType ? matchPage.path("total").asLong() : 0;
                long creditTotal = normalizedType.isBlank() || "ADMIN_CREDIT".equals(normalizedType)
                        ? creditPage.path("total").asLong() : 0;
                result.put("total", matchTotal + creditTotal);

                int consumedMatches = (int) selected.stream()
                        .filter(entry -> entry.source() == ActivitySource.MATCH).count();
                int consumedCredits = selected.size() - consumedMatches;
                ActivityCursor next = new ActivityCursor(
                        offsets.matches() + consumedMatches,
                        offsets.credits() + consumedCredits,
                        offsets.until());
                boolean hasMore = next.matches() < matchTotal || next.credits() < creditTotal;
                if (hasMore) result.put("nextCursor", encodeCursor(next));
                else result.putNull("nextCursor");
                return Response.ok(result).type(MediaType.APPLICATION_JSON).build();
            } catch (Exception exception) {
                LOG.error("Falha ao compor atividade administrativa", exception);
                return Response.serverError()
                        .entity("{\"message\":\"Falha ao compor atividade administrativa\"}").build();
            }
        });
    }

    private ObjectNode matchActivity(JsonNode source) {
        ObjectNode item = objectMapper.createObjectNode();
        item.put("type", source.path("eventType").asText());
        item.put("resourceId", source.path("matchId").asText());
        putNullable(item, "actorId", source.get("actorId"));
        putNullable(item, "reason", source.get("reason"));
        item.put("occurredAt", source.path("occurredAt").asText());
        return item;
    }

    private ObjectNode creditActivity(JsonNode source) {
        ObjectNode item = objectMapper.createObjectNode();
        item.put("type", "ADMIN_CREDIT");
        putNullable(item, "resourceId", source.get("referenceId"));
        putNullable(item, "actorId", source.get("createdBy"));
        putNullable(item, "reason", source.get("note"));
        item.put("amount", source.path("amount").asText());
        item.put("occurredAt", normalizeUtc(source.path("occurredAt").asText()));
        return item;
    }

    private void putNullable(ObjectNode target, String field, JsonNode source) {
        if (source == null || source.isNull() || source.asText().isBlank()) target.putNull(field);
        else target.put(field, source.asText());
    }

    private String normalizeUtc(String value) {
        try { return OffsetDateTime.parse(value).withOffsetSameInstant(ZoneOffset.UTC).toString(); }
        catch (Exception ignored) {
            try { return LocalDateTime.parse(value).atOffset(ZoneOffset.UTC).toString(); }
            catch (Exception invalid) { return value; }
        }
    }

    private OffsetDateTime occurredAt(JsonNode item) {
        try { return OffsetDateTime.parse(item.path("occurredAt").asText()); }
        catch (Exception ignored) { return OffsetDateTime.MIN; }
    }

    private ActivityCursor decodeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) return new ActivityCursor(0, 0, OffsetDateTime.now(ZoneOffset.UTC));
        try {
            String value = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] parts = value.split(";");
            if (parts.length != 3 || !parts[0].startsWith("matches:")
                    || !parts[1].startsWith("credits:") || !parts[2].startsWith("until:")) {
                throw new IllegalArgumentException();
            }
            return new ActivityCursor(
                    Math.max(0, Integer.parseInt(parts[0].substring(8))),
                    Math.max(0, Integer.parseInt(parts[1].substring(8))),
                    OffsetDateTime.parse(parts[2].substring(6)));
        } catch (Exception exception) {
            throw new BadRequestException("Cursor administrativo inválido");
        }
    }

    private String encodeCursor(ActivityCursor cursor) {
        String value = "matches:" + cursor.matches() + ";credits:" + cursor.credits()
                + ";until:" + cursor.until();
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private record ActivityCursor(int matches, int credits, OffsetDateTime until) {}
    private record ActivityEntry(ObjectNode value, ActivitySource source) {}
    private enum ActivitySource { MATCH, CREDIT }
}
