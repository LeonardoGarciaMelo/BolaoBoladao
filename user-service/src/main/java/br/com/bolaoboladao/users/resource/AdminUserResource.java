package br.com.bolaoboladao.users.resource;

import br.com.bolaoboladao.users.domain.User;
import br.com.bolaoboladao.users.domain.UserRole;
import br.com.bolaoboladao.users.dto.PageResponse;
import br.com.bolaoboladao.users.dto.UserResponse;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.Produces;

import java.util.List;
import java.util.Locale;
import java.util.Set;

@Path("/admin/users")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("ADMIN")
public class AdminUserResource {

    @GET
    public PageResponse<UserResponse> search(@QueryParam("q") @DefaultValue("") String query,
                                             @QueryParam("page") @DefaultValue("0") int page,
                                             @QueryParam("size") @DefaultValue("20") int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, 50));
        String normalized = "%" + query.trim().toLowerCase(Locale.ROOT) + "%";
        var panacheQuery = User.<User>find("lower(username) like ?1 or lower(name) like ?1 order by username", normalized);
        long total = panacheQuery.count();
        List<UserResponse> users = panacheQuery.page(safePage, safeSize).list().stream()
                .map(this::toResponse)
                .toList();
        return new PageResponse<>(users, safePage, safeSize, total);
    }

    private UserResponse toResponse(User user) {
        Set<String> roles = user.role == UserRole.ADMIN ? Set.of("USER", "ADMIN") : Set.of("USER");
        return new UserResponse(user.id, user.name, user.username, roles);
    }
}
