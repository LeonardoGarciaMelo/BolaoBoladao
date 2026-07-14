package br.com.bolaoboladao.partidas.resource;

import br.com.bolaoboladao.partidas.dto.TeamResponse;
import br.com.bolaoboladao.partidas.repository.TeamRepository;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

@Path("/admin/teams")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("ADMIN")
public class AdminTeamResource {
    @Inject TeamRepository teamRepository;

    @GET
    public List<TeamResponse> search(@QueryParam("q") @DefaultValue("") String query,
                                     @QueryParam("limit") @DefaultValue("20") int limit) {
        return teamRepository.search(query.trim(), Math.max(1, Math.min(limit, 50))).stream()
                .map(team -> new TeamResponse(team.id, team.name))
                .toList();
    }
}
