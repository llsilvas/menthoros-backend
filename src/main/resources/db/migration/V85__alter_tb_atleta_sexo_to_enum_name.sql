-- =====================================================================
-- V85: tb_atleta.sexo passa a guardar o nome do enum (MASCULINO/FEMININO/OUTRO)
--
-- A coluna era VARCHAR(1) com CHECK em M/F/O, mas o contrato com o front usa o nome
-- completo — todo PUT /api/v1/atletas com sexo caía em "value too long for type
-- character varying(1)". Alarga a coluna, converte os dados legados e refaz o CHECK.
-- =====================================================================

-- Há DOIS checks sobre a coluna: o inline sem nome do V1 (batizado pelo Postgres de
-- tb_atleta_sexo_check) e o ck_atleta_sexo do V6. Derruba todo CHECK que cite a coluna,
-- em vez de apostar nos nomes — ambientes recriados pelo V45 podem ter nenhum.
DO $$
DECLARE
    c RECORD;
BEGIN
    FOR c IN
        SELECT conname
        FROM pg_constraint
        WHERE conrelid = 'tb_atleta'::regclass
          AND contype = 'c'
          AND pg_get_constraintdef(oid) ILIKE '%sexo%'
    LOOP
        EXECUTE format('ALTER TABLE tb_atleta DROP CONSTRAINT %I', c.conname);
    END LOOP;
END$$;

ALTER TABLE tb_atleta ALTER COLUMN sexo TYPE VARCHAR(20);

UPDATE tb_atleta SET sexo = CASE sexo
    WHEN 'M' THEN 'MASCULINO'
    WHEN 'F' THEN 'FEMININO'
    WHEN 'O' THEN 'OUTRO'
    ELSE sexo
END
WHERE sexo IN ('M', 'F', 'O');

ALTER TABLE tb_atleta ADD CONSTRAINT ck_atleta_sexo
    CHECK (sexo IS NULL OR sexo IN ('MASCULINO', 'FEMININO', 'OUTRO'));

DO $$
BEGIN
    RAISE NOTICE '✅ V85 - tb_atleta.sexo convertido para nome do enum';
END$$;
