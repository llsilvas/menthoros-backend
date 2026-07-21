---
status: accepted
---

# Onboarding não oferece Strava como canal de integração, mesmo ativo em produção

Strava está em processo de descontinuação como integração do Menthoros (decisão do founder,
2026-07-21) — projeto próprio, fora do escopo de `athlete-onboarding-baseline`. `StravaOAuthServiceImpl`,
`StravaActivityServiceImpl`, webhooks e auto-sync continuam ativos em produção para atletas já
conectados; nada dessa infraestrutura é removida agora.

**Decisão:** o campo "canal de integração" do formulário de onboarding (novo, `athlete-onboarding-baseline`)
oferece apenas `INTERVALS_ICU` (com Garmin como device prioritário na orientação de conexão) e
`MANUAL` — Strava não aparece como opção para atletas novos a partir desta change, mesmo
continuando funcional para quem já usa. Evita crescer a base de atletas dependentes de uma
integração com prazo de vida definido.

**Consequência:** quando a descontinuação de Strava de fato acontecer (change própria, fora deste
escopo), a migração cobre só os atletas conectados antes desta decisão — a base de "novos"
atletas Strava para de crescer a partir de agora.
