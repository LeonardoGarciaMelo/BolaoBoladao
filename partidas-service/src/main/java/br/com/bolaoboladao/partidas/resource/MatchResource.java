package br.com.bolaoboladao.partidas.resource;

import br.com.bolaoboladao.partidas.dto.MatchEventResponse;
import br.com.bolaoboladao.partidas.dto.MatchResponse;
import br.com.bolaoboladao.partidas.mapper.MatchMapper;
import br.com.bolaoboladao.partidas.service.MatchService;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Path("/partidas")
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
