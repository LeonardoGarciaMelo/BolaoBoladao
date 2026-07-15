package br.com.bolaoboladao.partidas.resource;

import br.com.bolaoboladao.partidas.dto.MatchEventResponse;
import br.com.bolaoboladao.partidas.dto.MatchResponse;
import br.com.bolaoboladao.partidas.dto.PageResponse;
import br.com.bolaoboladao.partidas.domain.MatchStatus;
import br.com.bolaoboladao.partidas.mapper.MatchMapper;
import br.com.bolaoboladao.partidas.repository.MatchRepository;
import br.com.bolaoboladao.partidas.service.MatchService;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import java.util.List;
import java.util.UUID;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.stream.Collectors;

@Path("/partidas")
@Produces(MediaType.APPLICATION_JSON)
public class MatchResource {

    @Inject
    MatchService matchService;

    @Inject
    MatchRepository matchRepository;

    @GET
    public List<MatchResponse> listAll() {
        return matchService.findAll().stream()
                .map(MatchMapper::toResponse)
                .collect(Collectors.toList());
    }

    @GET
    @Path("/catalog")
    public PageResponse<MatchResponse> catalog(@QueryParam("view") @DefaultValue("OPEN") String view,
                                               @QueryParam("page") @DefaultValue("0") int page,
                                               @QueryParam("size") @DefaultValue("12") int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(50, size));
        var query = switch (view.trim().toUpperCase(java.util.Locale.ROOT)) {
            case "OPEN" -> matchRepository.find("status = ?1 and start > ?2 order by start asc",
                    MatchStatus.SCHEDULED, OffsetDateTime.now(ZoneOffset.UTC));
            case "LIVE" -> matchRepository.find("status = ?1 order by start desc", MatchStatus.IN_PROGRESS);
            case "FINISHED" -> matchRepository.find("status = ?1 order by end desc", MatchStatus.FINISHED);
            case "CANCELED" -> matchRepository.find("status = ?1 order by canceledAt desc", MatchStatus.CANCELED);
            default -> throw new BadRequestException("Filtro de catálogo inválido");
        };
        long total = query.count();
        List<MatchResponse> items = query.page(safePage, safeSize).list().stream()
                .map(MatchMapper::toResponse).toList();
        return new PageResponse<>(items, safePage, safeSize, total);
    }

    @GET
    @Path("/{id}")
    public MatchResponse findById(@PathParam("id") UUID id) {
        return matchService.findResponseById(id);
    }

    @GET
    @Path("/{id}/eventos")
    public List<MatchEventResponse> listEvents(@PathParam("id") UUID id) {
        return matchService.findEvents(id).stream()
                .map(MatchMapper::toResponse)
                .collect(Collectors.toList());
    }

}
