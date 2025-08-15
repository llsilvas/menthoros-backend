package com.menthoros.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/atletas") // ← Mapear URLs incorretas comuns
public class ErrorHandlerController {
    
    @PostMapping("/{atletaId}/gerar-enhanced")
    public ResponseEntity<Map<String, String>> gerarPlanoWrongURL(@PathVariable UUID atletaId) {
        return ResponseEntity.badRequest()
                .body(Map.of(
                    "error", "URL incorreta",
                    "message", "Use a URL correta: /planos/atletas/" + atletaId + "/gerar-enhanced",
                    "expected_base_url", "/planos/atletas/",
                    "received_base_url", "/atletas/"
                ));
    }
    
    @PostMapping("/{atletaId}/gerar-enhaced")
    public ResponseEntity<Map<String, String>> gerarPlanoTypo(@PathVariable UUID atletaId) {
        return ResponseEntity.badRequest()
                .body(Map.of(
                    "error", "URL com typo", 
                    "message", "Use: /planos/atletas/" + atletaId + "/gerar-enhanced (com 'n')",
                    "correct_url", "/planos/atletas/" + atletaId + "/gerar-enhanced"
                ));
    }
    
    @PostMapping("/{atletaId}/gerar")
    public ResponseEntity<Map<String, String>> gerarPlanoBasicWrongURL(@PathVariable UUID atletaId) {
        return ResponseEntity.badRequest()
                .body(Map.of(
                    "error", "URL incorreta",
                    "message", "Use a URL correta: /planos/atletas/" + atletaId + "/gerar",
                    "correct_url", "/planos/atletas/" + atletaId + "/gerar"
                ));
    }
}