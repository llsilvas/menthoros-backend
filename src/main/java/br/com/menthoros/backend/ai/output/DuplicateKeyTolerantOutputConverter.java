package br.com.menthoros.backend.ai.output;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.exc.StreamReadException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.converter.CompositeResponseTextCleaner;
import org.springframework.ai.converter.MarkdownCodeBlockCleaner;
import org.springframework.ai.converter.ResponseTextCleaner;
import org.springframework.ai.converter.ThinkingTagCleaner;
import org.springframework.ai.converter.WhitespaceCleaner;
import org.springframework.lang.NonNull;

/**
 * {@link BeanOutputConverter} que tolera chave repetida no JSON devolvido pelo LLM.
 *
 * Sem saída estruturada nativa (caso da Anthropic no Spring AI 1.1.6), nada impede o modelo de
 * repetir um campo. Com um record como alvo, o Jackson recusa a segunda ocorrência
 * ({@code No fallback setter/field defined for creator property}). Aqui o parse é estrito primeiro
 * — só para detectar a duplicata e deixá-la visível em log — e, se houver, refeito sobre a árvore,
 * onde o último valor de cada chave prevalece.
 *
 * O formato enviado no prompt ({@link #getFormat()}) é herdado: continua o do BeanOutputConverter.
 * Os mappers são próprios da instância — o ObjectMapper compartilhado da aplicação não é tocado.
 */
@Slf4j
public class DuplicateKeyTolerantOutputConverter<T> extends BeanOutputConverter<T> {

    private final Class<T> type;
    private final ResponseTextCleaner cleaner;
    private final ObjectMapper strictMapper;
    private final ObjectMapper lenientMapper;

    public DuplicateKeyTolerantOutputConverter(Class<T> type) {
        this(type, defaultCleaner());
    }

    private DuplicateKeyTolerantOutputConverter(Class<T> type, ResponseTextCleaner cleaner) {
        super(type, null, cleaner);
        this.type = type;
        this.cleaner = cleaner;
        // Mesma configuração do mapper default do BeanOutputConverter, para o resultado sem
        // duplicata ser idêntico ao de antes.
        this.lenientMapper = super.getObjectMapper();
        this.strictMapper = super.getObjectMapper();
        this.strictMapper.enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    }

    @Override
    public T convert(@NonNull String text) {
        String cleaned = cleaner.clean(text);
        try {
            return strictMapper.readValue(cleaned, type);
        } catch (StreamReadException e) {
            if (!isDuplicateKey(e)) {
                throw new RuntimeException(e);
            }
            // Só a mensagem do parser (nome do campo), nunca o corpo: a resposta pode trazer dado
            // do atleta.
            log.warn("Resposta do LLM com chave repetida para {}, usando o último valor: {}",
                    type.getSimpleName(), e.getOriginalMessage());
            return lenientParse(cleaned);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private T lenientParse(String cleaned) {
        try {
            return lenientMapper.treeToValue(lenientMapper.readTree(cleaned), type);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private static boolean isDuplicateKey(StreamReadException e) {
        String message = e.getOriginalMessage();
        return message != null && message.startsWith("Duplicate field");
    }

    private static ResponseTextCleaner defaultCleaner() {
        // Mesma cadeia do default (privado) do BeanOutputConverter.
        return CompositeResponseTextCleaner.builder()
                .addCleaner(new WhitespaceCleaner())
                .addCleaner(new ThinkingTagCleaner())
                .addCleaner(new MarkdownCodeBlockCleaner())
                .addCleaner(new WhitespaceCleaner())
                .build();
    }
}
