"""Monta o markdown da revisão humana cega de planos (migrate-openai-retired-models, task 3.5).

Lê target/revisao-cega/ (gerado por RevisaoCegaPlanosRealTest) e escreve um par A/B embaralhado por
arquétipo e repetição, com a chave no fim.
Uso: python3 -I docs/ia/revisao-cega-planos-montar.py <dir_entrada> <saida.md> <candidato_1> <candidato_2> [semente]
"""
import json, random, sys
from pathlib import Path

entrada, saida, cand = Path(sys.argv[1]), Path(sys.argv[2]), sys.argv[3:5]
ORDEM = ['SEGUNDA', 'TERCA', 'QUARTA', 'QUINTA', 'SEXTA', 'SABADO', 'DOMINGO']
# Avisos por arquétipo (vazio desde a rodada 2: a fixture do taper foi corrigida).
AVISO = {}


def v(x, suf=''):
    if x in (None, '', []):
        return '—'
    if isinstance(x, float):
        x = f'{x:.2f}'.rstrip('0').rstrip('.')
    return f'{x}{suf}'


def etapas(t):
    out = []
    for e in sorted(t.get('etapas') or [], key=lambda e: e.get('ordem') or 0):
        partes = [e.get('tipoEtapa') or '']
        if e.get('repeticoes') and e['repeticoes'] > 1:
            partes.append(f"{e['repeticoes']}×")
        if e.get('distanciaKm'):
            partes.append(f"{v(e['distanciaKm'])} km")
        if e.get('duracaoMin'):
            partes.append(f"{e['duracaoMin']} min")
        if e.get('ritmoAlvo'):
            partes.append(f"@ {e['ritmoAlvo']}")
        if e.get('fcAlvoEtapa'):
            partes.append(f"FC {e['fcAlvoEtapa']}")
        out.append(' '.join(partes))
    return '<br>'.join(out) or '—'


def plano(p):
    linhas = [f"**Objetivo da semana:** {v(p.get('objetivoSemanal'))}  ",
              f"**Volume planejado:** {v(p.get('volumePlanejadoKm'), ' km')} (alvo {v(p.get('volumeAlvoKm'), ' km')})\n",
              '| dia | tipo | distância | duração | ritmo | FC | RPE | etapas | descrição |',
              '|---|---|---|---|---|---|---|---|---|']
    treinos = sorted(p.get('treinosPlanejados') or [],
                     key=lambda t: ORDEM.index(t['diaSemana']) if t.get('diaSemana') in ORDEM else 9)
    for t in treinos:
        linhas.append(f"| {v(t.get('diaSemana'))} | {v(t.get('tipoTreino'))} | {v(t.get('distanciaKm'), ' km')} | "
                      f"{v(t.get('duracaoMin'))} | {v(t.get('ritmoAlvo'))} | {v(t.get('fcAlvo'))} | "
                      f"{v(t.get('percepcaoEsforcoEsperada'))} | {etapas(t)} | "
                      f"{v((t.get('descricao') or '').replace('|', '/'))} |")
    folgas = [f"{d.get('dayOfWeek')}" + (f" ({d['reason']})" if d.get('reason') else '') for d in p.get('restDays') or []]
    if folgas:
        linhas.append(f"\n**Descanso:** {', '.join(folgas)}")
    return '\n'.join(linhas)


random.seed(int(sys.argv[5]) if len(sys.argv) > 5 else 20261116)
md = ['# Revisão cega de planos — gpt-4o × sucessor (migrate-openai-retired-models, task 3.5)\n',
      'Cada caso é um arquétipo do eval set com o contexto do atleta e dois planos, A e B, gerados pelo',
      'mesmo prompt de produção e passados pela normalização de produção (o que o coach recebe). A ordem é',
      'embaralhada por caso; a chave está no fim.\n',
      '**Para cada caso, anote:** (1) qual você mandaria ao atleta: A, B ou empate; (2) se algum dos dois',
      'tem algo que você barraria (risco de lesão, carga errada, dia proibido, ritmo incoerente).\n',
      '**Nota da rodada 2:** um treino foi reprovado por um gate estrutural da normalização (em produção',
      'iria para o turno de reparo) e aparece como a LLM escreveu. A distância de cada treino contínuo é a',
      'soma das etapas, exceto quando o total da LLM é menor que o aquecimento e o desaquecimento gerados',
      'pelo sistema (aí a divergência é mostrada como veio).\n',
      '**Critério (CA4b):** o sucessor não pode ser preferido em menos casos que o gpt-4o, e não pode ter',
      'item que você barraria.\n']
chave, n = [], 0
for ctx in sorted(entrada.glob('*.contexto.txt')):
    arq = ctx.name.removesuffix('.contexto.txt')
    md.append(f"\n---\n\n# Arquétipo: {arq}\n")
    if arq in AVISO:
        md.append(AVISO[arq] + '\n')
    md.append('<details><summary>Contexto do atleta</summary>\n\n```\n' + ctx.read_text().strip() + '\n```\n\n</details>\n')
    r = 1
    while all((entrada / f"{arq}.{c.replace(':', '_')}.{r}.json").exists() for c in cand):
        n += 1
        ordem = cand[:]
        random.shuffle(ordem)
        chave.append(f"| {n} | {arq} | A = {ordem[0]} | B = {ordem[1]} |")
        md.append(f"\n## Caso {n} — {arq}, repetição {r}\n")
        for rotulo, c in zip('AB', ordem):
            dados = json.loads((entrada / f"{arq}.{c.replace(':', '_')}.{r}.json").read_text())
            md.append(f"\n### Plano {rotulo}\n\n{plano(dados)}\n")
        md.append('\n**Sua avaliação:** preferido: ___ · barraria algo? ___\n')
        r += 1
md.append('\n---\n\n<details><summary>Chave (abrir só depois de avaliar)</summary>\n\n| caso | arquétipo | | |\n|---|---|---|---|')
md += chave
md.append('\n</details>\n')
saida.write_text('\n'.join(md))
print(f"{n} casos em {saida}", file=sys.stderr)
