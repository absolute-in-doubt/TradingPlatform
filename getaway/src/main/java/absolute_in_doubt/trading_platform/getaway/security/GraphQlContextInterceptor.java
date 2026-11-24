package absolute_in_doubt.trading_platform.getaway.security;

import lombok.RequiredArgsConstructor;
import org.springframework.graphql.server.WebGraphQlInterceptor;
import org.springframework.graphql.server.WebGraphQlRequest;
import org.springframework.graphql.server.WebGraphQlResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;

@Component
@RequiredArgsConstructor
public class GraphQlContextInterceptor implements WebGraphQlInterceptor {

    private final JwtTokenService jwtTokenService;

    @Override
    public Mono<WebGraphQlResponse> intercept(WebGraphQlRequest request, Chain chain) {
        List<String> authHeaders = request.getHeaders().getOrEmpty("Authorization");
        if (!authHeaders.isEmpty()) {
            String header = authHeaders.get(0);
            if (header.startsWith("Bearer ")) {
                String token = header.substring(7).trim();
                UserPrincipal principal = jwtTokenService.parseToken(token);
                if (principal != null) {
                    request.configureExecutionInput((executionInput, builder) ->
                            builder.graphQLContext(Collections.singletonMap("currentUser", principal)).build());
                }
            }
        }
        return chain.next(request);
    }
}
