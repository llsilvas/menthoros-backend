package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.dto.DecisaoProgressao;
import br.com.menthoros.backend.dto.input.DadosPlanoDto;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.PlanoMetaDados;
import br.com.menthoros.backend.entity.Prova;
import br.com.menthoros.backend.entity.RevisaoSemanal;
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Tudo que a geração de plano lê do banco ANTES de chamar o LLM, carregado numa transação curta
 * pelo {@link PlanGenerationContextLoader} e consumido depois dela — durante a chamada ao modelo
 * e na transação de escrita do {@link PlanGenerationPersister}.
 *
 * <p>As entidades aqui dentro ({@code atleta}, {@code metaDados}, {@code revisaoConsumida},
 * {@code proximaProva}) estão <b>detached</b>: o loader inicializa explicitamente todo caminho
 * lazy que o fluxo lê depois da fronteira (provas, dias disponíveis, assessoria). Quem adicionar
 * um acesso lazy novo depois do LLM precisa adicioná-lo também no loader — o
 * {@code PlanGenerationContextLoaderIT} percorre esses acessos sem transação ativa e é o que
 * quebra quando isso é esquecido.
 *
 * <p>Compõe {@link DadosPlanoDto} em vez de substituí-lo: o {@code PlannerShadowService} continua
 * recebendo o DTO que já conhece.
 *
 * @param dados              atleta, metadados, histórico de 42 dias e plano anterior
 * @param decisaoProgressao  decisão de progressão, ou {@code null} quando o cálculo falhou
 * @param semanaInicio       semana do plano, resolvida uma única vez para prompt e persistência
 * @param revisaoConsumida   revisão da semana anterior que alimenta o prompt, ou {@code null}
 * @param proximaProva       prova alvo (ou a mais próxima) do atleta, ou {@code null}
 */
public record PlanGenerationContext(DadosPlanoDto dados,
                                    @Nullable DecisaoProgressao decisaoProgressao,
                                    LocalDate semanaInicio,
                                    @Nullable RevisaoSemanal revisaoConsumida,
                                    @Nullable Prova proximaProva) {

    public PlanGenerationContext {
        if (dados == null) {
            throw new IllegalArgumentException("dados do plano são obrigatórios");
        }
        if (semanaInicio == null) {
            throw new IllegalArgumentException("semanaInicio é obrigatória");
        }
    }

    public Atleta atleta() {
        return dados.atleta();
    }

    public PlanoMetaDados metaDados() {
        return dados.metaDados();
    }

    public Optional<RevisaoSemanal> revisaoConsumidaOpcional() {
        return Optional.ofNullable(revisaoConsumida);
    }
}
