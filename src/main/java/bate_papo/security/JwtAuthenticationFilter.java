package bate_papo.security;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.List;

public class JwtAuthenticationFilter implements WebFilter {

    private final JwtUtil jwtUtil;

    public JwtAuthenticationFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String token = null;
        if (exchange.getRequest().getCookies().getFirst("jwt") != null) {
            token = exchange.getRequest().getCookies().getFirst("jwt").getValue();
        }

        if (token != null && jwtUtil.validateToken(token)) {

            String username = jwtUtil.extractUsername(token);

            return chain.filter(exchange)
                    .contextWrite(ReactiveSecurityContextHolder.withAuthentication(
                            new UsernamePasswordAuthenticationToken(username, null, List.of())
                    ));
        }
        return chain.filter(exchange);
    }
}