package bate_papo.service;

import bate_papo.exception.UsernameAlreadyExistsException;
import bate_papo.model.User;
import bate_papo.repository.UserRepository;
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

    public Mono<User> register(User user) {
        return userRepository.findByUsername(user.getUsername())
                .flatMap(existingUser -> {
                    return Mono.error(new UsernameAlreadyExistsException("Username already taken."));
                })
                .then(Mono.defer(() -> {
                    user.setPassword(passwordEncoder.encode(user.getPassword()));
                    return userRepository.save(user);
                }));
    }

    public Mono<User> login(User user) {
        return userRepository.findByUsername(user.getUsername())
                .filter(u -> passwordEncoder.matches(user.getPassword(), u.getPassword()))
                .switchIfEmpty(Mono.error(new RuntimeException("Invalid credentials.")));
    }
}