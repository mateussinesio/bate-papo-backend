package bate_papo.controller;

import bate_papo.model.ChatMessage;
import bate_papo.service.ChatService;
import bate_papo.websocket.ChatWebSocketHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/chat")
public class ChatController {

    @Autowired
    private ChatService chatService;

    @Autowired
    private ChatWebSocketHandler chatWebSocketHandler;

    @GetMapping("/messages")
    public Flux<ChatMessage> getAllMessages() {
        return chatService.getAllMessages();
    }

    @PostMapping("/send")
    public Mono<ChatMessage> sendMessage(@RequestBody ChatMessage chatMessage) {
        return chatService.saveMessage(chatMessage);
    }

    @PutMapping("/edit-message/{id}")
    public Mono<ResponseEntity<ChatMessage>> editMessage(@PathVariable String id, @RequestBody ChatMessage updatedMessage) {
        return chatService.editMessage(id, updatedMessage)
                .flatMap(chatMessage -> chatWebSocketHandler.broadcastEditMessage(chatMessage)
                        .then(Mono.just(ResponseEntity.ok(chatMessage))));
    }

    @DeleteMapping("/delete-message/{id}")
    public Mono<ResponseEntity<Void>> deleteMessage(@PathVariable String id) {
        return chatService.deleteMessage(id)
                .then(chatWebSocketHandler.broadcastDeleteMessage(id))
                .then(Mono.just(ResponseEntity.noContent().build()));
    }

    @DeleteMapping("/delete-all-messages/{username}")
    public Mono<ResponseEntity<Void>> deleteAllMessages(@PathVariable String username) {
        return chatService.deleteAllMessagesByUser(username)
                .then(chatWebSocketHandler.broadcastDeleteAllMessages(username))
                .then(Mono.just(ResponseEntity.noContent().build()));
    }
}