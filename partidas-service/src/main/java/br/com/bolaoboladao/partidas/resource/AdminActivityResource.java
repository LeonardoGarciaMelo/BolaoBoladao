package br.com.bolaoboladao.partidas.resource;

import br.com.bolaoboladao.partidas.domain.MatchEvent;
import br.com.bolaoboladao.partidas.domain.MatchEventType;
import br.com.bolaoboladao.partidas.dto.MatchEventResponse;
import br.com.bolaoboladao.partidas.dto.PageResponse;
import br.com.bolaoboladao.partidas.mapper.MatchMapper;
import br.com.bolaoboladao.partidas.repository.MatchEventRepository;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Path("/admin/activity")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("ADMIN")
public class AdminActivityResource {
    @Inject MatchEventRepository repository;

    @GET
    public PageResponse<MatchEventResponse> activity(@QueryParam("offset") @DefaultValue("0") int offset,
                                                     @QueryParam("size") @DefaultValue("20") int size,
                                                     @QueryParam("type") String type,
                                                     @QueryParam("until") OffsetDateTime until) {
        int safeOffset = Math.max(0, offset);
        int safeSize = Math.max(1, Math.min(size, 50));
        OffsetDateTime snapshot = until == null ? OffsetDateTime.now(ZoneOffset.UTC) : until;
        var query = "MATCH_CREATED".equals(type)
                ? repository.find("eventType = ?1 and occurredAt <= ?2 order by occurredAt desc",
                        MatchEventType.MATCH_CREATED, snapshot)
                : "MATCH_CANCELED".equals(type)
                ? repository.find("eventType = ?1 and occurredAt <= ?2 order by occurredAt desc",
                        MatchEventType.MATCH_CANCELED, snapshot)
                : repository.find("eventType in (?1, ?2) and occurredAt <= ?3 order by occurredAt desc",
                        MatchEventType.MATCH_CREATED, MatchEventType.MATCH_CANCELED, snapshot);
        long total = query.count();
        var items = query.range(safeOffset, safeOffset + safeSize - 1).list().stream()
                .map(MatchMapper::toResponse).toList();
        return new PageResponse<>(items, safeOffset, safeSize, total);
    }
}
