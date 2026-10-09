-- =====================================================================
-- V102: Adiciona o ciclo de vida de status ao lead da waitlist (add-waitlist-status-lifecycle)
-- Aditivo, sem backfill — leads existentes ficam com os 4 campos NULL (status NEW/INVITED
-- aparente, mesmo quem já converteu antes desta change; histórico real continua em
-- tb_founding_invite.converted_at/assessoria_id).
-- =====================================================================

ALTER TABLE tb_waitlist
    ADD COLUMN IF NOT EXISTS invited_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS activated_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS discarded_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS assessoria_id UUID;

COMMENT ON COLUMN tb_waitlist.invited_at IS
    'Quando o convite da turma fundadora foi enviado com sucesso para este lead.';

COMMENT ON COLUMN tb_waitlist.activated_at IS
    'Quando o convite foi convertido em assessoria (CoachSignupServiceImpl.consumirConvite).';

COMMENT ON COLUMN tb_waitlist.discarded_at IS
    'Quando o lead foi descartado manualmente. Nenhuma rota preenche esta coluna ainda '
    '(add-waitlist-status-lifecycle) — existe para não exigir outra migration quando houver.';

COMMENT ON COLUMN tb_waitlist.assessoria_id IS
    'Assessoria criada a partir deste lead, quando convertido. Sem FK, mesmo padrão solto de '
    'tb_founding_invite.assessoria_id.';

DO $$
BEGIN
    RAISE NOTICE '✅ V102 - invited_at, activated_at, discarded_at e assessoria_id adicionadas a tb_waitlist';
END$$;
