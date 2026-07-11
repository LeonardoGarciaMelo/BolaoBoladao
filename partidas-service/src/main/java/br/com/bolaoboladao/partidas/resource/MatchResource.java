package br.com.bolaoboladao.partidas.resource;

import br.com.bolaoboladao.partidas.domain.Match;
import br.com.bolaoboladao.partidas.dto.CreateMatchRequest;
import br.com.bolaoboladao.partidas.dto.MatchEventResponse;
import br.com.bolaoboladao.partidas.dto.MatchResponse;
import br.com.bolaoboladao.partidas.dto.ScoreEventRequest;
import br.com.bolaoboladao.partidas.mapper.MatchMapper;
import br.com.bolaoboladao.partidas.service.MatchService;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Path("/partidas")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class MatchResource {

    @Inject
    MatchService matchService;

    @GET
    public List<MatchResponse> listAll() {
        return matchService.findAll().stream()
                .map(MatchMapper::toResponse)
                .collect(Collectors.toList());
    }

    @GET
    @Path("/{id}")
    public MatchResponse findById(@PathParam("id") UUID id) {
        Match match = matchService.findByIdOrThrow(id);
        return MatchMapper.toResponse(match);
    }

    @GET
    @Path("/{id}/eventos")
    public List<MatchEventResponse> listEvents(@PathParam("id") UUID id) {
        return matchService.findEvents(id).stream()
                .map(MatchMapper::toResponse)
                .collect(Collectors.toList());
    }

    @POST
    public Response create(@Valid CreateMatchRequest request, @jakarta.ws.rs.core.Context jakarta.ws.rs.core.UriInfo uriInfo) {
        Match match = matchService.createMatch(request);
        var location = UriBuilder.fromResource(MatchResource.class)
                .path(match.id.toString())
                .build();
        return Response.created(location).entity(MatchMapper.toResponse(match)).build();
    }

    @POST
    @Path("/{id}/iniciar")
    public MatchResponse start(@PathParam("id") UUID id) {
        Match match = matchService.startMatch(id);
        return MatchMapper.toResponse(match);
    }

    @POST
    @Path("/{id}/gol")
    public MatchResponse registerScore(@PathParam("id") UUID id, @Valid ScoreEventRequest request) {
        Match match = matchService.registerScore(id, request);
        return MatchMapper.toResponse(match);
    }

    @POST
    @Path("/{id}/encerrar")
    public MatchResponse end(@PathParam("id") UUID id) {
        Match match = matchService.endMatch(id);
        return MatchMapper.toResponse(match);
    }
}
