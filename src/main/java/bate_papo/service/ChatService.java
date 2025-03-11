package bate_papo.service;

import bate_papo.model.ChatMessage;
import bate_papo.repository.ChatMessageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

@Service
public class ChatService {

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private Sinks.Many<ChatMessage> sink;

    @Autowired
    private EncryptionService encryptionService;

    public Mono<ChatMessage> saveMessage(ChatMessage chatMessage) {
        return encryptionService.encryptMessage(chatMessage.getContent())
                .flatMap(encryptedText -> {
                    chatMessage.setContent(encryptedText);
                    return chatMessageRepository.save(chatMessage)
                            .doOnNext(savedMessage -> sink.tryEmitNext(savedMessage));
                });
    }

    public Flux<ChatMessage> getAllMessages() {
        return chatMessageRepository.findAll()
                .flatMap(chatMessage ->
                        encryptionService.decryptMessage(chatMessage.getContent())
                                .map(decryptedText -> {
                                    chatMessage.setContent(decryptedText);
                                    return chatMessage;
                                })
                );
    }

    public Mono<ChatMessage> editMessage(String id, String newContent) {
        return chatMessageRepository.findById(id)
                .flatMap(existingMessage -> {
                    return encryptionService.encryptMessage(newContent)
                            .flatMap(encryptedText -> {
                                existingMessage.setContent(encryptedText);
                                return chatMessageRepository.save(existingMessage)
                                        .doOnNext(savedMessage -> sink.tryEmitNext(savedMessage));
                            });
                });
    }

    public Mono<Void> deleteMessage(String id) {
        return chatMessageRepository.deleteById(id);
    }

    public Mono<Void> deleteAllMessagesByUser(String username) {
        return chatMessageRepository.deleteAll(chatMessageRepository.findAll()
                .filter(message -> message.getSender().equals(username)));
    }
}