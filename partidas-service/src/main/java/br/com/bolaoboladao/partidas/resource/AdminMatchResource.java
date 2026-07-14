package br.com.bolaoboladao.partidas.resource;

import br.com.bolaoboladao.partidas.domain.Match;
import br.com.bolaoboladao.partidas.domain.MatchStatus;
import br.com.bolaoboladao.partidas.dto.CancelMatchRequest;
import br.com.bolaoboladao.partidas.dto.CreateMatchRequest;
import br.com.bolaoboladao.partidas.dto.MatchResponse;
import br.com.bolaoboladao.partidas.dto.PageResponse;
import br.com.bolaoboladao.partidas.dto.ScoreEventRequest;
import br.com.bolaoboladao.partidas.mapper.MatchMapper;
import br.com.bolaoboladao.partidas.repository.MatchRepository;
import br.com.bolaoboladao.partidas.service.MatchService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.List;
import java.util.UUID;

@Path("/admin/partidas")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("ADMIN")
public class AdminMatchResource {
    @Inject MatchService matchService;
    @Inject MatchRepository matchRepository;
    @Inject JsonWebToken token;

    @GET
    public PageResponse<MatchResponse> list(@QueryParam("status") MatchStatus status,
                                            @QueryParam("page") @DefaultValue("0") int page,
                                            @QueryParam("size") @DefaultValue("20") int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(50, size));
        var query = status == null
                ? matchRepository.find("order by start desc")
                : matchRepository.find("status = ?1 order by start desc", status);
        long total = query.count();
        List<MatchResponse> items = query.page(safePage, safeSize).list().stream()
                .map(MatchMapper::toResponse).toList();
        return new PageResponse<>(items, safePage, safeSize, total);
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response create(@Valid CreateMatchRequest request) {
        Match match = matchService.createMatch(request, UUID.fromString(token.getSubject()));
        return Response.status(Response.Status.CREATED).entity(MatchMapper.toResponse(match)).build();
    }

    @POST
    @Path("/{id}/cancel")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response cancel(@PathParam("id") UUID id, @Valid CancelMatchRequest request,
                           @HeaderParam("Idempotency-Key") String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BadRequestException("Idempotency-Key é obrigatório");
        }
        Match match = matchService.cancelMatch(id, request.reason(), UUID.fromString(token.getSubject()), idempotencyKey);
        return Response.accepted(MatchMapper.toResponse(match)).build();
    }

    @POST @Path("/{id}/iniciar")
    public MatchResponse start(@PathParam("id") UUID id) {
        return MatchMapper.toResponse(matchService.startMatch(id));
    }

    @POST @Path("/{id}/gol")
    @Consumes(MediaType.APPLICATION_JSON)
    public MatchResponse score(@PathParam("id") UUID id, @Valid ScoreEventRequest request) {
        return MatchMapper.toResponse(matchService.registerScore(id, request));
    }

    @POST @Path("/{id}/encerrar")
    public MatchResponse end(@PathParam("id") UUID id) {
        return MatchMapper.toResponse(matchService.endMatch(id));
    }
}
