-- =====================================================================
-- V80: estado do wizard de boas-vindas do coach (coach-first-login-wizard)
-- =====================================================================
--
-- DEFAULT true, E ISSO E DELIBERADO — o contrario do reflexo.
-- A coluna marca "ja passou pelo onboarding". Quem ja usa o produto nao pode
-- ser interrompido por um wizard de boas-vindas no proximo login, entao toda
-- linha existente nasce concluida. O default tambem protege qualquer caminho
-- de criacao de Usuario que ninguem lembrou de auditar: o pior erro possivel
-- aqui e prender um usuario legitimo num wizard, nao deixar de exibi-lo.
--
-- QUEM GRAVA false: apenas CoachSignupServiceImpl, ao criar o Usuario do
-- fundador. E o unico lugar, e ha teste fixando isso.
--
-- POR QUE NAO CONFIAR NO DEFAULT DO JAVA: `boolean` primitivo nasce false na
-- JVM — exatamente o valor errado para o negocio. Se o campo fosse populado
-- pela entidade em vez do banco, todo usuario sincronizado pelo
-- UsuarioSyncServiceImpl (que roda a cada requisicao) entraria pendente.
--
-- POR QUE NAO E COLUNA EM tb_assessoria: o onboarding e por USUARIO. Um
-- tecnico convidado depois nao deve reabrir o wizard do dono, nem o inverso.

ALTER TABLE tb_usuario
    ADD COLUMN IF NOT EXISTS onboarding_concluido BOOLEAN NOT NULL DEFAULT true;

DO $$
BEGIN
    RAISE NOTICE '✅ V80 - coluna onboarding_concluido adicionada em tb_usuario';
END$$;
