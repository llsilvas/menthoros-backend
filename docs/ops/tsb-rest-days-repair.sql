-- =====================================================================
-- fix-tsb-rest-days-materialization: diagnostico e validacao do reparo
-- =====================================================================
-- SOMENTE LEITURA. Nao ha reparo em SQL: o reparo e o proprio
-- TsbDecaimentoDiarioScheduler (cron "0 30 * * * *", America/Sao_Paulo), que
-- detecta buraco e atraso por atleta e chama recalcularDesde ate o "hoje" dele.
-- recalcularHistoricoCompleto NAO faz parte do reparo: processa em blocos de
-- 30 dias, sem rollback global.
--
-- Roteiro (base do homelab; confirmar antes que a conexao aponta para ela):
--   1. ANTES do deploy: rodar a consulta 1 e salvar a saida
--        psql "$DATABASE_URL" -f docs/ops/tsb-rest-days-repair.sql \
--             -o docs/ops/tsb-rest-days-antes.txt
--   2. Deploy da change. Esperar a proxima execucao do job (minuto 30 de cada hora)
--      e conferir no log "[tsb-decaimento] processados=... falhas=0".
--      Atraso acima de 400 dias avanca 400 dias por execucao.
--   3. DEPOIS: rodar de novo, salvar em docs/ops/tsb-rest-days-depois.txt.
--      Consulta 1 deve voltar vazia; consulta 2 sem linhas "fora".
--   4. Conferir o gauge tsb.atletas_defasados = 0 em /actuator/prometheus.
-- =====================================================================

\echo '== 1. Atletas com buraco ou com ultima metrica antes de hoje (esperado depois do reparo: vazio) =='
WITH serie AS (
    SELECT m.atleta_id,
           MIN(m.data) AS primeira,
           MAX(m.data) AS ultima,
           COUNT(*)    AS linhas
    FROM tb_metricas_diarias m
    GROUP BY m.atleta_id
), diag AS (
    SELECT s.*,
           (now() AT TIME ZONE COALESCE(a.timezone, 'America/Sao_Paulo'))::date AS hoje,
           CASE WHEN s.linhas < (s.ultima - s.primeira + 1) THEN (
               SELECT MIN(g.dia)::date
               FROM generate_series(s.primeira::timestamp, s.ultima::timestamp, INTERVAL '1 day') AS g(dia)
               WHERE NOT EXISTS (SELECT 1 FROM tb_metricas_diarias x
                                 WHERE x.atleta_id = s.atleta_id AND x.data = g.dia::date)
           ) END AS primeiro_buraco
    FROM serie s
    JOIN tb_atleta a ON a.id = s.atleta_id
)
SELECT d.atleta_id,
       d.primeira,
       d.ultima,
       d.linhas,
       (d.ultima - d.primeira + 1) - d.linhas AS dias_faltando,
       d.primeiro_buraco,
       d.hoje - d.ultima                      AS dias_de_atraso,
       (SELECT m.ctl FROM tb_metricas_diarias m
         WHERE m.atleta_id = d.atleta_id AND m.data = d.ultima) AS ctl_na_ultima_linha
FROM diag d
WHERE d.primeiro_buraco IS NOT NULL OR d.ultima < d.hoje
ORDER BY d.atleta_id;

\echo '== 2. Validacao do decaimento: dia de descanso (TSS 0) deve ter CTL = CTL do dia anterior * exp(-1/tau) =='
\echo '   tolerancia 0.05 (valores gravados com 2 casas). "fora" > 0 indica seed errado.'
WITH rest AS (
    SELECT m.atleta_id, m.data, m.ctl, p.ctl AS ctl_anterior,
           COALESCE(a.ctl_time_constant,
                    CASE a.nivel_experiencia WHEN 'INICIANTE' THEN 30
                                             WHEN 'INTERMEDIARIO' THEN 35
                                             WHEN 'ELITE' THEN 50
                                             ELSE 42 END) AS tau
    FROM tb_metricas_diarias m
    JOIN tb_metricas_diarias p ON p.atleta_id = m.atleta_id AND p.data = m.data - 1
    JOIN tb_atleta a ON a.id = m.atleta_id
    WHERE m.tss = 0
)
SELECT r.atleta_id,
       COUNT(*)                                                                AS dias_de_descanso,
       ROUND(MAX(ABS(r.ctl - r.ctl_anterior * EXP(-1.0 / r.tau)))::numeric, 4) AS maior_desvio,
       COUNT(*) FILTER (WHERE ABS(r.ctl - r.ctl_anterior * EXP(-1.0 / r.tau)) > 0.05) AS fora
FROM rest r
GROUP BY r.atleta_id
ORDER BY fora DESC, maior_desvio DESC;
