package bate_papo.service;

import bate_papo.dto.UserResponseDTO;
import bate_papo.exception.UsernameAlreadyExistsException;
import bate_papo.model.User;
import bate_papo.repository.UserRepository;
import bate_papo.security.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class AuthenticationService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    public Mono<UserResponseDTO> register(User user) {
        return userRepository.findByUsername(user.getUsername())
                .flatMap(existingUser -> Mono.error(new UsernameAlreadyExistsException("Username already taken.")))
                .then(Mono.defer(() -> {
                    user.setPassword(passwordEncoder.encode(user.getPassword()));
                    return userRepository.save(user)
                            .map(savedUser -> new UserResponseDTO(savedUser.getUsername()));
                }));
    }

    public Mono<String> login(String username, String password) {
        return userRepository.findByUsername(username)
                .filter(u -> passwordEncoder.matches(password, u.getPassword()))
                .switchIfEmpty(Mono.error(new RuntimeException("Invalid credentials.")))
                .map(u -> jwtUtil.generateToken(u.getUsername()));
    }
}