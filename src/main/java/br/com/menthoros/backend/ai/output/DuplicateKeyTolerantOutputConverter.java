package br.com.menthoros.backend.ai.output;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.core.exc.StreamReadException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.converter.CompositeResponseTextCleaner;
import org.springframework.ai.converter.MarkdownCodeBlockCleaner;
import org.springframework.ai.converter.ResponseTextCleaner;
import org.springframework.ai.converter.StructuredOutputConverter;
import org.springframework.ai.converter.ThinkingTagCleaner;
import org.springframework.ai.converter.WhitespaceCleaner;
import org.springframework.ai.util.JacksonUtils;
import org.springframework.lang.NonNull;

/**
 * Conversor de saída estruturada que tolera chave repetida no JSON devolvido pelo LLM.
 *
 * Sem saída estruturada nativa (caso da Anthropic no Spring AI 1.1.6), nada impede o modelo de
 * repetir um campo. Com um record como alvo, o Jackson recusa a segunda ocorrência
 * ({@code No fallback setter/field defined for creator property}). Aqui o parse é estrito primeiro
 * — só para detectar a duplicata e deixá-la visível em log — e, se houver, refeito sobre a árvore,
 * onde o último valor de cada chave prevalece.
 *
 * O formato enviado no prompt vem de um {@link BeanOutputConverter} interno, então as instruções de
 * schema são as mesmas de {@code .entity(Class)}. Os mappers são próprios da instância (o
 * ObjectMapper compartilhado da aplicação não é tocado) e não incluem o texto de origem nas
 * mensagens de erro: essas mensagens vão para log e para {@code AnaliseWorkout.errorMessage}, e a
 * resposta pode trazer dado do atleta.
 */
@Slf4j
public class DuplicateKeyTolerantOutputConverter<T> implements StructuredOutputConverter<T> {

    // Mesma cadeia do default (privado) do BeanOutputConverter. Uma divergência futura é pega pelo
    // teste de equivalência com o BeanOutputConverter.
    private static final ResponseTextCleaner CLEANER = CompositeResponseTextCleaner.builder()
            .addCleaner(new WhitespaceCleaner())
            .addCleaner(new ThinkingTagCleaner())
            .addCleaner(new MarkdownCodeBlockCleaner())
            .addCleaner(new WhitespaceCleaner())
            .build();

    private final Class<T> type;
    private final BeanOutputConverter<T> formatProvider;
    private final ObjectMapper strictMapper;
    private final ObjectMapper lenientMapper;

    public DuplicateKeyTolerantOutputConverter(Class<T> type) {
        this.type = type;
        this.lenientMapper = mapper(false);
        this.strictMapper = mapper(true);
        this.formatProvider = new BeanOutputConverter<>(type, lenientMapper);
    }

    @Override
    public String getFormat() {
        return formatProvider.getFormat();
    }

    @Override
    public T convert(@NonNull String text) {
        String cleaned = CLEANER.clean(text);
        try {
            return strictMapper.readValue(cleaned, type);
        } catch (StreamReadException e) {
            if (!isDuplicateKey(e)) {
                throw new RuntimeException(e);
            }
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

    // Detecção pela mensagem do Jackson, que não é contrato público: se uma atualização mudar o
    // texto, a duplicata volta a falhar alto (FAILED), sem corromper dado — e o teste de duplicata
    // acusa na hora.
    private static boolean isDuplicateKey(StreamReadException e) {
        String message = e.getOriginalMessage();
        return message != null && message.startsWith("Duplicate field");
    }

    // Mesma configuração do mapper default do BeanOutputConverter, para o resultado sem duplicata
    // ser idêntico ao de antes.
    private static ObjectMapper mapper(boolean strict) {
        JsonMapper.Builder builder = JsonMapper.builder()
                .addModules(JacksonUtils.instantiateAvailableModules())
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .disable(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION);
        if (strict) {
            builder.enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
        }
        return builder.build();
    }
}
