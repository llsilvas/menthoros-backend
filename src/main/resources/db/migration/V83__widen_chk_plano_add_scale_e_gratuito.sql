-- Alarga chk_plano (V2) para aceitar SCALE (plano novo do lançamento do MVP) e GRATUITO
-- (já existia no enum Java, mas nunca tinha entrado no CHECK do banco). Aditivo: nenhuma
-- linha existente viola o novo CHECK, só amplia os valores aceitos.
ALTER TABLE tb_assessoria DROP CONSTRAINT chk_plano;
ALTER TABLE tb_assessoria ADD CONSTRAINT chk_plano
    CHECK (plano IN ('GRATUITO', 'BASIC', 'PRO', 'ENTERPRISE', 'SCALE'));
