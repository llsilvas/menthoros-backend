---
status: accepted
---

# Acesso a dado de saúde do onboarding é por tenant, não por técnico responsável

`athlete-onboarding-baseline` (CA12) precisava decidir quem lê campos de saúde (lesão, dor,
fadiga, sono, recuperação) coletados no onboarding e durante `CALIBRATION`. A decisão original
era "atleta + técnico responsável pelo atleta" — mas o modelo hoje não tem essa relação: uma
`Atleta` está vinculada a uma `Assessoria` (tenant), e uma `Assessoria` pode ter vários técnicos
(`TECNICO`/`ADMIN`), sem nenhum vínculo individual "este técnico é responsável por este atleta".

Construir essa relação (`AtletaTecnicoResponsavel` ou equivalente) é escopo maior que um
onboarding baseline — decisão do usuário (2026-07-21): fica para uma change própria no futuro.

**Decisão:** por ora, acesso a dado de saúde do onboarding segue o mesmo modelo do resto do
perfil do atleta — qualquer `TECNICO`/`ADMIN` do tenant vê, não só um técnico designado.

**Consequência:** assessorias com múltiplos técnicos não têm isolamento de dado sensível entre
atletas de técnicos diferentes dentro do mesmo tenant. Se isso virar requisito de produto antes
da change de "técnico responsável" existir, CA12 precisa ser revisitado.
