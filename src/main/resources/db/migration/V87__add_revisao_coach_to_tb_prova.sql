-- =====================================================================
-- V87: ciência do coach sobre provas tocadas pelo atleta (atleta-cadastra-prova, D6)
-- =====================================================================
--
-- O atleta passa a criar, alterar e cancelar as próprias provas. Toda mudança dele zera
-- revisada_pelo_coach e grava o motivo (NOVA, DATA_ALTERADA, ALVO_TROCADA, CANCELADA); na
-- troca de alvo, alvo_anterior_nome guarda a prova substituída — a fila de atenção é
-- calculada a cada GET e não teria como reconstituir isso. O "Ciente" do coach volta a flag
-- para true e limpa os dois campos.
--
-- Default true: provas já existentes nascem revisadas para não inundar a fila no deploy.
-- Aditiva; rollback: reverter o código, colunas ficam inertes.

ALTER TABLE tb_prova
    ADD COLUMN IF NOT EXISTS revisada_pelo_coach BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS motivo_revisao VARCHAR(20) NULL,
    ADD COLUMN IF NOT EXISTS alvo_anterior_nome VARCHAR(100) NULL;

CREATE INDEX IF NOT EXISTS idx_prova_pendente_revisao
    ON tb_prova (atleta_id)
    WHERE revisada_pelo_coach = FALSE;

DO $$
BEGIN
    RAISE NOTICE '✅ V87 - colunas de ciência do coach adicionadas a tb_prova';
END$$;
