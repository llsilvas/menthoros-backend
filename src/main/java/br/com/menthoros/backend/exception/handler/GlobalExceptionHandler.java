package br.com.menthoros.backend.exception.handler;


import br.com.menthoros.backend.exception.DuplicateResourceException;
import br.com.menthoros.backend.exception.LLMException;
import br.com.menthoros.backend.exception.ResourceNotFoundException;
import jakarta.persistence.OptimisticLockException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.HashMap;
import java.util.Map;


@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {


    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("status", 400);
        body.put("error", "Bad Request");
        Map<String, String> fields = new HashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fields.put(fe.getField(), fe.getDefaultMessage());
        }
        body.put("fields", fields);
        return ResponseEntity.badRequest().body(body);
    }


    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(ResourceNotFoundException ex) {
        Map<String, Object> body = Map.of(
                "status", 404,
                "error", "Not Found",
                "message", ex.getMessage()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }


    @ExceptionHandler(OptimisticLockException.class)
    public ResponseEntity<Map<String, Object>> handleConflict(OptimisticLockException ex) {
        Map<String, Object> body = Map.of(
                "status", 409,
                "error", "Conflict",
                "message", ex.getMessage()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }


    @ExceptionHandler({DuplicateResourceException.class, DataIntegrityViolationException.class})
    public ResponseEntity<Map<String, Object>> handleDuplicate(Exception ex) {
        Map<String, Object> body = Map.of(
                "status", 409,
                "error", "Conflict",
                "message", ex.getMessage()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }


    @ExceptionHandler(LLMException.class)
    public ResponseEntity<Map<String, Object>> handleLLMException(LLMException ex) {
        log.error("Erro no serviço de LLM: {}", ex.getMessage(), ex);
        Map<String, Object> body = Map.of(
                "status", 503,
                "error", "Service Unavailable",
                "message", "Serviço de IA temporariamente indisponível. Tente novamente em alguns instantes.",
                "details", ex.getMessage()
        );
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntimeException(RuntimeException ex) {
        if (ex.getMessage() != null && ex.getMessage().contains("OpenAI")) {
            log.error("Erro na comunicação com OpenAI: {}", ex.getMessage(), ex);
            Map<String, Object> body = Map.of(
                    "status", 502,
                    "error", "Bad Gateway",
                    "message", "Erro na comunicação com serviços externos. Tente novamente."
            );
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(body);
        }
        return handleGeneric(ex);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Argumento inválido: {}", ex.getMessage());
        Map<String, Object> body = Map.of(
                "status", 400,
                "error", "Bad Request",
                "message", ex.getMessage()
        );
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<Map<String, Object>> handleMissingHeader(MissingRequestHeaderException ex) {
        log.warn("Header obrigatório ausente: {}", ex.getHeaderName());
        Map<String, Object> body = Map.of(
                "status", 400,
                "error", "Bad Request",
                "message", "Header obrigatório ausente: " + ex.getHeaderName()
        );
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNoResourceFound(NoResourceFoundException ex) {
        log.warn("Recurso não encontrado: {}", ex.getResourcePath());
        Map<String, Object> body = Map.of(
                "status", 404,
                "error", "Not Found",
                "message", "Recurso não encontrado: " + ex.getResourcePath()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
        log.error("Erro interno não tratado: {}", ex.getMessage(), ex);
        Map<String, Object> body = Map.of(
                "status", 500,
                "error", "Internal Server Error",
                "message", "Erro interno do servidor. Contate o suporte se o problema persistir."
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
