package bate_papo.controller;

import bate_papo.dto.LoginRequestDTO;
import bate_papo.exception.UsernameAlreadyExistsException;
import bate_papo.model.User;
import bate_papo.security.JwtUtil;
import bate_papo.service.AuthenticationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/auth")
public class AuthenticationController {

    @Autowired
    private AuthenticationService authenticationService;

    @Autowired
    private JwtUtil jwtUtil;

    @PostMapping("/register")
    public Mono<ResponseEntity<String>> register(@RequestBody User user) {
        return authenticationService.register(user)
                .map(registeredUser -> ResponseEntity.status(HttpStatus.CREATED).body("User registered successfully."))
                .onErrorResume(UsernameAlreadyExistsException.class, e ->
                        Mono.just(ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage())))
                .onErrorResume(Exception.class, e ->
                        Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("An error occurred during registration.")));
    }

    @PostMapping("/login")
    public Mono<ResponseEntity<String>> login(@RequestBody LoginRequestDTO loginRequest, ServerHttpResponse response) {
        return authenticationService.login(loginRequest.username(), loginRequest.password())
                .map(token -> {
                    ResponseCookie jwtCookie = ResponseCookie.from("jwt", token)
                            .httpOnly(true)
                            .secure(true)
                            .path("/")
                            .sameSite("Strict")
                            .maxAge(86400)
                            .build();

                    response.addCookie(jwtCookie);

                    return ResponseEntity.ok("Login successful.");
                })
                .onErrorResume(e -> Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid credentials.")));
    }

    @GetMapping("/userinfo")
    public Mono<ResponseEntity<String>> getUserInfo(@CookieValue(name = "jwt", required = false) String token) {
        if (token == null || !jwtUtil.validateToken(token)) {
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid or missing token."));
        }

        String username = jwtUtil.extractUsername(token);

        if (username != null) {
            return Mono.just(ResponseEntity.ok(username));
        } else {
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Token does not contain a valid username."));
        }
    }

    @GetMapping("/validate")
    public Mono<ResponseEntity<Boolean>> validateToken(@CookieValue(name = "jwt", required = false) String token) {
        if (token != null && jwtUtil.validateToken(token)) {
            return Mono.just(ResponseEntity.ok(true));
        }
        return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(false));
    }

    @PostMapping("/logout")
    public Mono<ResponseEntity<Void>> logout(ServerHttpResponse response) {
        ResponseCookie deleteCookie = ResponseCookie.from("jwt", "")
                .httpOnly(true)
                .secure(true)
                .path("/")
                .sameSite("Strict")
                .maxAge(0)
                .build();

        response.addCookie(deleteCookie);
        return Mono.just(ResponseEntity.ok().build());
    }
}