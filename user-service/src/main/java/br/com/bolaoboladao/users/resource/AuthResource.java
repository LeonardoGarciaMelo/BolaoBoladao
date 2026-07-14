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
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@Path("/auth")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class AuthResource {

    @Inject
    UserService userService;

    @Inject
    TokenService tokenService;

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

    private UserResponse toResponse(User user) {
        return new UserResponse(user.id, user.name, user.username);
    }
}
