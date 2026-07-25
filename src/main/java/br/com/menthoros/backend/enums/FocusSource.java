package br.com.menthoros.backend.enums;

/**
 * Como o {@code nextWeekFocus} de uma {@link br.com.menthoros.backend.entity.RevisaoSemanal} foi
 * produzido (termo "Origem do Foco" no CONTEXT.md).
 *
 * <p>Mesma razão da {@link OrigemAprovacao}: uma vez persistido, um foco escrito por LLM e um
 * template determinístico são indistinguíveis. Sem essa distinção não há como medir se a narrativa
 * por IA muda o comportamento do coach — que é a contrapartida de valor do gate de custo A1.
 */
public enum FocusSource {

    /** Narrativa redigida por LLM e aprovada pelo checker de consistência. */
    LLM,

    /**
     * Template determinístico derivado do {@code recommendationType}. É o valor quando a flag está
     * desligada, quando a chamada ao LLM falha, e quando a narrativa é reprovada pelo checker.
     */
    TEMPLATE
}
