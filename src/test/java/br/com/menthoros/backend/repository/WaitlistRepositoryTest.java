package br.com.menthoros.backend.repository;

import br.com.menthoros.backend.AbstractIntegrationTest;
import br.com.menthoros.backend.entity.Waitlist;
import br.com.menthoros.backend.enums.PerfilWaitlist;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V99 {@code docs_notified_at} + a reivindicação atômica que protege
 * {@code notify-waitlist-docs-site} contra duas chamadas concorrentes processando o mesmo
 * inscrito (design D3).
 *
 * <p>{@code @Transactional} na classe: cada teste roda e desfaz na sua própria transação —
 * necessário aqui porque as consultas são de tabela inteira (todo TREINADOR pendente), não por
 * id específico, e vazariam entre métodos de teste sem o rollback automático. Isso protege a
 * escrita <strong>deste</strong> teste de vazar para os outros, mas não isola a leitura de linhas
 * já comitadas por <strong>outras</strong> classes de teste no mesmo Postgres compartilhado da
 * suíte (ex.: {@code WaitlistControllerIT}) — por isso as asserções abaixo checam presença/
 * ausência do registro próprio ({@code contains}/{@code doesNotContain}), nunca a lista inteira.</p>
 */
@Transactional
@DisplayName("WaitlistRepository: elegíveis para o aviso da central de ajuda e claim atômico")
class WaitlistRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private WaitlistRepository repository;

    private Waitlist inserirInscrito(PerfilWaitlist perfil, Instant docsNotifiedAt) {
        // Sem .id(...): GenerationType.UUID precisa gerar o id no persist. Pré-setá-lo faz o
        // Hibernate tratar a entidade como "destacada" e tentar merge() em vez de persist(),
        // falhando com StaleObjectStateException (não há linha para atualizar ainda).
        var email = "coach-" + UUID.randomUUID() + "@exemplo.com";
        return repository.save(Waitlist.builder()
                .nome("Coach")
                .email(email)
                .emailNormalized(email)
                .perfil(perfil)
                .aceiteLgpd(true)
                .docsNotifiedAt(docsNotifiedAt)
                .build());
    }

    @Nested
    @DisplayName("findAllByPerfilInAndDocsNotifiedAtIsNull")
    class Elegiveis {

        @Test
        @DisplayName("só retorna TREINADOR sem docsNotifiedAt")
        void filtraPerfilEAviso() {
            var pendente = inserirInscrito(PerfilWaitlist.TREINADOR, null);
            var ataleta = inserirInscrito(PerfilWaitlist.ATLETA, null); // CA3 — perfil errado, nunca entra
            var jaAvisado = inserirInscrito(PerfilWaitlist.TREINADOR, Instant.now());

            var ids = repository.findAllByPerfilInAndDocsNotifiedAtIsNull(List.of(PerfilWaitlist.TREINADOR))
                    .stream().map(Waitlist::getId).toList();

            assertThat(ids).contains(pendente.getId())
                    .doesNotContain(ataleta.getId(), jaAvisado.getId());
        }

        @Test
        @DisplayName("já avisado não entra na lista de elegíveis — CA5/idempotência")
        void jaAvisadoNaoEElegivel() {
            var avisado = inserirInscrito(PerfilWaitlist.TREINADOR, Instant.now());

            var ids = repository.findAllByPerfilInAndDocsNotifiedAtIsNull(List.of(PerfilWaitlist.TREINADOR))
                    .stream().map(Waitlist::getId).toList();

            assertThat(ids).doesNotContain(avisado.getId());
        }

        @Test
        @DisplayName("lista com múltiplos perfis retorna TREINADOR e PROPRIETARIO (expand-waitlist-access-contract)")
        void aceitaMultiplosPerfis() {
            var treinador = inserirInscrito(PerfilWaitlist.TREINADOR, null);
            var proprietario = inserirInscrito(PerfilWaitlist.PROPRIETARIO, null);
            var atleta = inserirInscrito(PerfilWaitlist.ATLETA, null);

            var ids = repository.findAllByPerfilInAndDocsNotifiedAtIsNull(
                            List.of(PerfilWaitlist.TREINADOR, PerfilWaitlist.PROPRIETARIO))
                    .stream().map(Waitlist::getId).toList();

            assertThat(ids).contains(treinador.getId(), proprietario.getId())
                    .doesNotContain(atleta.getId());
        }
    }

    @Nested
    @DisplayName("reivindicarAvisoDocs / liberarAvisoDocs — CA8")
    class Claim {

        @Test
        @DisplayName("primeira reivindicação ganha (1); a segunda, para o mesmo id, perde (0)")
        void reivindicacaoEAtomica() {
            var inscrito = inserirInscrito(PerfilWaitlist.TREINADOR, null);
            var agora = Instant.now();

            int primeira = repository.reivindicarAvisoDocs(inscrito.getId(), agora);
            int segunda = repository.reivindicarAvisoDocs(inscrito.getId(), agora);

            assertThat(primeira).isEqualTo(1);
            assertThat(segunda).isEqualTo(0);
        }

        @Test
        @DisplayName("liberar depois de uma falha torna o inscrito elegível de novo")
        void liberarReabreElegibilidade() {
            var inscrito = inserirInscrito(PerfilWaitlist.TREINADOR, null);
            repository.reivindicarAvisoDocs(inscrito.getId(), Instant.now());

            repository.liberarAvisoDocs(inscrito.getId());

            var ids = repository.findAllByPerfilInAndDocsNotifiedAtIsNull(List.of(PerfilWaitlist.TREINADOR))
                    .stream().map(Waitlist::getId).toList();
            assertThat(ids).contains(inscrito.getId());
        }
    }
}
