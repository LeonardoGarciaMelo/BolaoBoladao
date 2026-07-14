package br.com.bolaoboladao.users.resource;

import br.com.bolaoboladao.users.domain.User;
import br.com.bolaoboladao.users.dto.AuthResponse;
import br.com.bolaoboladao.users.dto.LoginRequest;
import br.com.bolaoboladao.users.dto.RegisterRequest;
import br.com.bolaoboladao.users.dto.UserResponse;
import br.com.bolaoboladao.users.service.TokenService;
import br.com.bolaoboladao.users.service.UserService;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.jwt.JsonWebToken;
import io.quarkus.security.Authenticated;

import java.util.Set;
import java.util.UUID;

@Path("/auth")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class AuthResource {

    @Inject
    UserService userService;

    @Inject
    TokenService tokenService;

    @Inject
    JsonWebToken token;

    @ConfigProperty(name = "jwt.expiration-seconds")
    long expirationSeconds;

    @POST
    @Path("/register")
    public Response register(@Valid RegisterRequest request) {
        User user = userService.register(request);
        return Response.status(Response.Status.CREATED).entity(toResponse(user)).build();
    }

    @POST
    @Path("/login")
    public AuthResponse login(@Valid LoginRequest request) {
        User user = userService.authenticate(request);
        return new AuthResponse(tokenService.issue(user), "Bearer", expirationSeconds);
    }

    @GET
    @Path("/me")
    @Authenticated
    public UserResponse me() {
        User user = User.findById(UUID.fromString(token.getSubject()));
        if (user == null) {
            throw new jakarta.ws.rs.NotAuthorizedException("Bearer");
        }
        return toResponse(user);
    }

    private UserResponse toResponse(User user) {
        Set<String> roles = user.role == br.com.bolaoboladao.users.domain.UserRole.ADMIN
                ? Set.of("USER", "ADMIN")
                : Set.of("USER");
        return new UserResponse(user.id, user.name, user.username, roles);
    }
}
