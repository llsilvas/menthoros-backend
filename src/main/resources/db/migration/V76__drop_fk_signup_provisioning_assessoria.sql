-- =====================================================================
-- V76: remove a FK de tb_signup_provisioning.assessoria_id
-- =====================================================================
--
-- A V75 declarou ON DELETE SET NULL para que a compensação pudesse apagar a
-- assessoria sem ser bloqueada pela FK. A intenção estava certa; o efeito, não.
--
-- A compensação apaga a assessoria e o Postgres zera a coluna — mas a entidade
-- que o serviço ainda tem em memória carrega o id antigo, e o UPDATE seguinte
-- (o que grava o desfecho FAILED) reescreve esse id, agora pendurado. A FK
-- dispara e derruba justamente a linha que registra a falha. Consequências
-- medidas em CoachSignupCompensacaoIT, contra Keycloak e Postgres reais:
--
--   1. o rastro nunca chega a FAILED — congela no último passo bem-sucedido,
--      e a varredura por RECONCILIATION_REQUIRED nunca o encontra;
--   2. quem se cadastrou recebe um 500 sobre constraint de banco, no lugar da
--      causa real, que fica só no log;
--   3. a métrica conta "falha_compensada" antes do UPDATE, então contador e
--      rastro passam a discordar.
--
-- Nenhum teste unitário alcançaria isto: sem banco não há FK.
--
-- A coluna passa a ser UUID solto, deliberadamente. tb_signup_provisioning é
-- trilha de auditoria de recursos que ela mesma manda apagar: guardar QUAL
-- assessoria existiu é o ponto, e integridade referencial para uma linha que o
-- fluxo remove de propósito é uma contradição. Mesma convenção já adotada em
-- tenant_id, que também não tem FK e é gerido pela aplicação.
--
-- Trade-off aceito: o id fica pendurado após a remoção. É perícia, não
-- navegação — nada faz join a partir dele.

ALTER TABLE tb_signup_provisioning
    DROP CONSTRAINT IF EXISTS tb_signup_provisioning_assessoria_id_fkey;

DO $$
BEGIN
    RAISE NOTICE '✅ V76 - FK de tb_signup_provisioning.assessoria_id removida (id preservado para perícia)';
END$$;
