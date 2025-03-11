package bate_papo.controller;

import bate_papo.dto.*;
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
    public Flux<ChatMessageResponseDTO> getAllMessages() {
        return chatService.getAllMessages()
                .map(chatMessage -> new ChatMessageResponseDTO(
                        chatMessage.getId(),
                        chatMessage.getSender(),
                        chatMessage.getContent(),
                        chatMessage.getTimestamp()
                ));
    }

    @PostMapping("/send")
    public Mono<ResponseEntity<ChatMessageResponseDTO>> sendMessage(@RequestBody ChatMessageRequestDTO chatMessageRequest) {
        ChatMessage chatMessage = new ChatMessage();
        chatMessage.setSender(chatMessageRequest.sender());
        chatMessage.setContent(chatMessageRequest.content());

        return chatService.saveMessage(chatMessage)
                .map(savedMessage -> ResponseEntity.ok(new ChatMessageResponseDTO(
                        savedMessage.getId(),
                        savedMessage.getSender(),
                        savedMessage.getContent(),
                        savedMessage.getTimestamp()
                )));
    }

    @PutMapping("/edit-message/{id}")
    public Mono<ResponseEntity<ChatMessageResponseDTO>> editMessage(
            @PathVariable String id,
            @RequestBody EditMessageRequestDTO editMessageRequest
    ) {
        return chatService.editMessage(id, editMessageRequest.content())
                .flatMap(updatedMessage -> chatWebSocketHandler.broadcastEditMessage(updatedMessage)
                        .then(Mono.just(ResponseEntity.ok(new ChatMessageResponseDTO(
                                updatedMessage.getId(),
                                updatedMessage.getSender(),
                                updatedMessage.getContent(),
                                updatedMessage.getTimestamp()
                        )))));
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