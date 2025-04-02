package bate_papo.websocket;

import bate_papo.model.ChatMessage;
import bate_papo.repository.ChatMessageRepository;
import bate_papo.security.JwtUtil;
import bate_papo.service.EncryptionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class ChatWebSocketHandler implements WebSocketHandler {

    private final List<WebSocketSession> sessions = new CopyOnWriteArrayList<>();
    private final EncryptionService encryptionService;
    private final ChatMessageRepository chatMessageRepository;
    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ChatWebSocketHandler(EncryptionService encryptionService,
                                ChatMessageRepository chatMessageRepository,
                                JwtUtil jwtUtil) {
        this.encryptionService = encryptionService;
        this.chatMessageRepository = chatMessageRepository;
        this.jwtUtil = jwtUtil;
    }

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        String token = session.getHandshakeInfo().getCookies().getFirst("jwt") != null
                ? session.getHandshakeInfo().getCookies().getFirst("jwt").getValue()
                : null;

        if (token == null || !jwtUtil.validateToken(token)) {
            return session.close();
        }

        String username = jwtUtil.extractUsername(token);

        return session.receive()
                .filter(message -> message.getType() == WebSocketMessage.Type.TEXT)
                .map(WebSocketMessage::getPayloadAsText)
                .flatMap(messageJson -> {
                    try {
                        ChatMessage receivedMessage = objectMapper.readValue(messageJson, ChatMessage.class);

                        return encryptionService.encryptMessage(receivedMessage.getContent())
                                .flatMap(encryptedMessage -> {
                                    ChatMessage chatMessage = new ChatMessage(
                                            receivedMessage.getSender(),
                                            encryptedMessage,
                                            LocalDateTime.now()
                                    );

                                    return chatMessageRepository.save(chatMessage)
                                            .flatMap(savedMessage -> {
                                                return encryptionService.decryptMessage(savedMessage.getContent())
                                                        .flatMap(decryptedMessage -> {
                                                            return broadcastMessage(savedMessage, decryptedMessage);
                                                        });
                                            });
                                });
                    } catch (Exception e) {
                        return Mono.error(new RuntimeException("Error processing message", e));
                    }
                })
                .then()
                .doFirst(() -> sessions.add(session))
                .doFinally(signal -> sessions.remove(session));
    }

    public Mono<Void> broadcastEditMessage(ChatMessage chatMessage) {
        return encryptionService.decryptMessage(chatMessage.getContent())
                .flatMap(decryptedMessage -> {
                    String json = String.format(
                            "{\"action\":\"edit\",\"id\":\"%s\",\"content\":\"%s\"}",
                            chatMessage.getId(),
                            decryptedMessage
                    );

                    return Flux.fromIterable(sessions)
                            .filter(WebSocketSession::isOpen)
                            .flatMap(session -> session.send(Mono.just(session.textMessage(json))))
                            .then();
                });
    }

    public Mono<Void> broadcastDeleteMessage(String id) {
        String json = String.format(
                "{\"action\":\"delete\",\"id\":\"%s\"}",
                id
        );

        return Flux.fromIterable(sessions)
                .filter(WebSocketSession::isOpen)
                .flatMap(session -> session.send(Mono.just(session.textMessage(json))))
                .then();
    }

    public Mono<Void> broadcastDeleteAllMessages(String username) {
        String json = String.format(
                "{\"action\":\"deleteAll\",\"username\":\"%s\"}",
                username
        );

        return Flux.fromIterable(sessions)
                .filter(WebSocketSession::isOpen)
                .flatMap(session -> session.send(Mono.just(session.textMessage(json))))
                .then();
    }

    private Mono<Void> broadcastMessage(ChatMessage chatMessage, String decryptedMessage) {
        String json = String.format(
                "{\"id\":\"%s\",\"sender\":\"%s\",\"content\":\"%s\",\"timestamp\":\"%s\"}",
                chatMessage.getId(),
                chatMessage.getSender(),
                decryptedMessage,
                chatMessage.getTimestamp().toString()
        );

        return Flux.fromIterable(sessions)
                .filter(WebSocketSession::isOpen)
                .flatMap(session -> session.send(Mono.just(session.textMessage(json))))
                .then();
    }
}