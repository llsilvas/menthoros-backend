package com.menthoros.services;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class ChatService {

    private final ChatClient chatClient;

    public ChatService(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public String responder(String pergunta){
        return getString(pergunta);
    }

    private String getString(String pergunta) {
        return chatClient.prompt()
                .user(pergunta)
                .call()
                .content();
    }
}

