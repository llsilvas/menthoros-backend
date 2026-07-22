---
status: accepted
---

# Rascunho de onboarding não escreve direto em `Atleta` até a conclusão

`tb_perfil_onboarding_atleta` (CA8, onboarding retomável) só persiste os 5 campos genuinamente
novos do onboarding — os outros 7 campos obrigatórios (objetivo, nivelExperiencia,
diasDisponiveis, dados de lesão, volumeSemanalMax) já existem em `Atleta`. A opção óbvia era
escrever esses 7 campos direto em `Atleta` a cada step do formulário — mais simples, sem
duplicação de escrita.

**Problema com a opção óbvia:** enquanto o atleta está em `RASCUNHO` (onboarding incompleto,
abandonável a qualquer momento), `Atleta` já teria dado parcial gravado permanentemente. Qualquer
fluxo que já lê `Atleta` direto (geração de plano, perfil do atleta pro coach) não distingue "dado
definitivo" de "rascunho abandonado no meio" — não checa `status` de `tb_perfil_onboarding_atleta`.

**Decisão (2026-07-21):** os 7 campos espelhados ficam em `tb_perfil_onboarding_atleta` durante o
rascunho (não em `Atleta`). Só na conclusão (`status: RASCUNHO -> COMPLETO`) é que todos os 11
campos migram para `Atleta`, numa unica transação. Mais escrita duplicada durante o rascunho, mas
`Atleta` nunca fica com dado parcial de um onboarding não concluído.

**Risco simétrico aceito e mitigado:** essa escolha reabre o risco oposto — o coach edita `Atleta`
via CRUD normal enquanto o atleta está em rascunho, e a conclusão sobrescreve com dado desatualizado
do formulário. Mitigação: na conclusão, comparar `Atleta.atualizadoEm` com a última atualização do
rascunho (`PerfilOnboardingAtleta.atualizadoEm`, não o início — correção QA 2026-07-21: comparar
contra o início travaria a conclusão permanentemente após qualquer edição direta, mesmo se o atleta
revisitasse e salvasse o rascunho de novo reconhecendo o estado atual); se `Atleta` foi modificada
depois da última vez que o rascunho foi salvo, bloquear a migração com erro em vez de aplicar
last-write-wins silencioso (ver design.md Decisão 10).
