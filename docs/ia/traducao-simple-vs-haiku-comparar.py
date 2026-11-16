"""Task 3.1 de consolidate-llm-on-openai: traduções Haiku x rota simple, lado a lado e às cegas.

Gera 10 análises em inglês com o prompt de produção (SKILL.md + template, Sonnet, temp 0.2) a partir
de treinos sintéticos, traduz cada campo com o template de produção em três modelos e grava markdown.
Uso: python3 -I comparar.py <repo_root> <env_file> <saida.md>
"""
import json, random, re, sys, time, urllib.request
from pathlib import Path

repo, env_file, saida = Path(sys.argv[1]), Path(sys.argv[2]), Path(sys.argv[3])
env = dict(l.split('=', 1) for l in env_file.read_text().splitlines() if '=' in l and not l.startswith('#'))
OPENAI = env['OPENAI_API_KEY'].strip().strip('"\'')
ANTHROPIC = env['ANTHROPIC_API_KEY'].strip().strip('"\'')

SKILL = (repo / 'src/main/resources/skills/analise/workout-analyzer/SKILL.md').read_text()
USER_TPL = (repo / 'src/main/resources/prompts/workout-analysis-user-prompt.txt').read_text()
TRAD_TPL = (repo / 'src/main/resources/prompts/translate-field-prompt.txt').read_text()
CAMPOS = ['summary', 'technical_interpretation', 'recommendation', 'rationale']


def post(url, headers, body):
    req = urllib.request.Request(url, json.dumps(body).encode(), {**headers, 'content-type': 'application/json'})
    with urllib.request.urlopen(req, timeout=120) as r:
        return json.load(r)


def claude(model, system, user, temp, max_tokens):
    body = {'model': model, 'max_tokens': max_tokens, 'temperature': temp, 'messages': [{'role': 'user', 'content': user}]}
    if system:
        body['system'] = system
    r = post('https://api.anthropic.com/v1/messages', {'x-api-key': ANTHROPIC, 'anthropic-version': '2023-06-01'}, body)
    return r['content'][0]['text'], r['usage']['input_tokens'], r['usage']['output_tokens']


def openai(model, user, temp, max_tokens, effort=None):
    body = {'model': model, 'temperature': temp, 'max_completion_tokens': max_tokens,
            'messages': [{'role': 'user', 'content': user}]}
    if effort:
        body['reasoning_effort'] = effort
    r = post('https://api.openai.com/v1/chat/completions', {'authorization': 'Bearer ' + OPENAI}, body)
    return r['choices'][0]['message']['content'], r['usage']['prompt_tokens'], r['usage']['completion_tokens']


# Perfis de rota do application.yml: standard (Haiku, 0.5, 2000) e simple (0.3, 1000).
TRADUTORES = {
    'claude-haiku-4-5 (standard hoje)': lambda t: claude('claude-haiku-4-5-20251001', None, t, 0.5, 2000),
    'gpt-4o-mini (simple hoje)': lambda t: openai('gpt-4o-mini', t, 0.3, 1000),
    'gpt-6-luna none (simple candidato)': lambda t: openai('gpt-6-luna', t, 0.3, 1000, 'none'),
}


def etapa(ordem, tipo, dur=None, dist=None, hr=None, pace=None, reps=None):
    return {k: v for k, v in dict(order=ordem, type=tipo, duration_min=dur, distance_km=dist, hr_target=hr,
                                  pace_target=pace, repetitions=reps).items() if v is not None}


def real(ordem, tipo, dur, dist, hr, mx, rpe, pace):
    return dict(order=ordem, type=tipo, duration_min=dur, distance_km=dist, avg_hr=hr, max_hr=mx, rpe=rpe,
                avg_pace_min_km=pace)


TREINOS = [
    ('rodagem leve executada', {'type': 'RODAGEM', 'distance_km': 8, 'expected_rpe': 3, 'duration_min': 48},
     {'distance_km': 8.1, 'rpe': 3, 'avg_hr': 142, 'duration_min': 48, 'avg_pace_min_km': 5.93}, {'tsb': 4.0, 'ctl': 42.0}),
    ('rodagem leve forte demais', {'type': 'RODAGEM', 'distance_km': 10, 'expected_rpe': 3, 'duration_min': 60},
     {'distance_km': 10, 'rpe': 6, 'avg_hr': 164, 'duration_min': 52, 'avg_pace_min_km': 5.2}, {'tsb': -12.5, 'ctl': 55.0}),
    ('intervalado 6x800 no alvo', {'type': 'INTERVALADO', 'distance_km': 9, 'expected_rpe': 7, 'duration_min': 55,
                                   'steps': [etapa(1, 'AQUECIMENTO', 15, hr='Z2'), etapa(2, 'TIRO', dist=0.8, pace='4:10', reps=6), etapa(3, 'DESAQUECIMENTO', 10)]},
     {'distance_km': 9.2, 'rpe': 7, 'avg_hr': 158, 'duration_min': 54, 'avg_pace_min_km': 5.0,
      'steps': [real(1, 'AQUECIMENTO', 15, 2.6, 138, 145, 3, 5.77), real(2, 'TIRO', 20, 4.8, 172, 181, 8, 4.15), real(3, 'DESAQUECIMENTO', 10, 1.7, 140, 150, 3, 5.88)]}, {'tsb': 1.0, 'ctl': 48.0}),
    ('intervalado com queda nos tiros finais', {'type': 'INTERVALADO', 'distance_km': 10, 'expected_rpe': 8, 'duration_min': 60,
                                               'steps': [etapa(1, 'AQUECIMENTO', 15), etapa(2, 'TIRO', dist=1.0, pace='3:55', reps=5), etapa(3, 'DESAQUECIMENTO', 10)]},
     {'distance_km': 9.4, 'rpe': 9, 'avg_hr': 166, 'duration_min': 61, 'avg_pace_min_km': 5.1,
      'steps': [real(1, 'AQUECIMENTO', 15, 2.7, 140, 148, 3, 5.55), real(2, 'TIRO', 24, 5.0, 178, 189, 9, 4.12), real(3, 'DESAQUECIMENTO', 10, 1.6, 150, 160, 4, 6.25)]}, {'tsb': -18.0, 'ctl': 61.0}),
    ('longão encurtado', {'type': 'LONGO', 'distance_km': 24, 'expected_rpe': 5, 'duration_min': 140},
     {'distance_km': 17.3, 'rpe': 7, 'avg_hr': 155, 'duration_min': 103, 'avg_pace_min_km': 5.95}, {'tsb': -9.0, 'ctl': 58.0}),
    ('longão com calor', {'type': 'LONGO', 'distance_km': 18, 'expected_rpe': 5, 'duration_min': 108},
     {'distance_km': 18.1, 'rpe': 7, 'avg_hr': 161, 'duration_min': 115, 'avg_pace_min_km': 6.35}, {'tsb': 3.0, 'ctl': 50.0}),
    ('tempo run acima do limiar', {'type': 'TEMPO', 'distance_km': 8, 'expected_rpe': 6, 'duration_min': 40,
                                   'steps': [etapa(1, 'AQUECIMENTO', 10), etapa(2, 'RITMO', 20, pace='4:35'), etapa(3, 'DESAQUECIMENTO', 10)]},
     {'distance_km': 8.4, 'rpe': 8, 'avg_hr': 168, 'duration_min': 39, 'avg_pace_min_km': 4.64,
      'steps': [real(1, 'AQUECIMENTO', 10, 1.8, 141, 150, 3, 5.55), real(2, 'RITMO', 20, 4.6, 176, 184, 8, 4.35), real(3, 'DESAQUECIMENTO', 9, 2.0, 150, 158, 4, 4.5)]}, {'tsb': -6.0, 'ctl': 52.0}),
    ('regenerativo ignorado', {'type': 'REGENERATIVO', 'distance_km': 5, 'expected_rpe': 2, 'duration_min': 35},
     {'distance_km': 11.0, 'rpe': 5, 'avg_hr': 153, 'duration_min': 60, 'avg_pace_min_km': 5.45}, {'tsb': -22.0, 'ctl': 64.0}),
    ('fartlek iniciante', {'type': 'FARTLEK', 'distance_km': 6, 'expected_rpe': 5, 'duration_min': 40},
     {'distance_km': 5.6, 'rpe': 6, 'avg_hr': 159, 'duration_min': 41, 'avg_pace_min_km': 7.32}, {'tsb': 6.0, 'ctl': 22.0}),
    ('progressivo bem executado', {'type': 'PROGRESSIVO', 'distance_km': 12, 'expected_rpe': 6, 'duration_min': 62},
     {'distance_km': 12.0, 'rpe': 6, 'avg_hr': 151, 'duration_min': 61, 'avg_pace_min_km': 5.08}, {'tsb': 2.5, 'ctl': 47.0}),
]


def numeros(t):
    return set(re.findall(r'\d+(?:[.,]\d+)?', t or ''))


def analisar(planned, actual, ctx):
    dados = json.dumps({'planned': planned, 'actual': actual, 'athlete_context': ctx})
    texto, _, _ = claude('claude-sonnet-4-6', SKILL, USER_TPL % dados, 0.2, 4000)
    m = re.search(r'\{.*\}', texto, re.S)
    return json.loads(m.group(0))


random.seed(20261115)
linhas, chave, alertas = [], [], []
lat = {n: [] for n in TRADUTORES}
toks = {n: [0, 0] for n in TRADUTORES}
for i, (nome, planned, actual, ctx) in enumerate(TREINOS, 1):
    analise = analisar(planned, actual, ctx)
    nomes = list(TRADUTORES)
    random.shuffle(nomes)
    rotulos = dict(zip(nomes, 'ABC'))
    chave.append(f"| {i} | " + ' | '.join(f"{rotulos[n]} = {n}" for n in TRADUTORES) + ' |')
    traducoes = {}
    for n in TRADUTORES:
        inicio = time.monotonic()
        traducoes[n] = {}
        for c in CAMPOS:
            src = analise.get(c) or ''
            if not src.strip():
                traducoes[n][c] = src
                continue
            out, ti, to = TRADUTORES[n](TRAD_TPL % src)
            toks[n][0] += ti
            toks[n][1] += to
            traducoes[n][c] = out.strip()
            falta = numeros(src) - numeros(out.replace(',', '.')) - numeros(out)
            if not out.strip():
                alertas.append(f"caso {i}, {c}, {n}: campo vazio")
            elif falta:
                alertas.append(f"caso {i}, {c}, {n}: números ausentes {sorted(falta)}")
        lat[n].append(time.monotonic() - inicio)
    linhas.append(f"\n## Caso {i} — {nome}\n")
    for c in CAMPOS:
        linhas.append(f"\n### {c}\n\n**Original (en):** {analise.get(c)}\n")
        for n in sorted(nomes, key=lambda x: rotulos[x]):
            linhas.append(f"- **{rotulos[n]}:** {traducoes[n][c]}")
    print(f"caso {i} ok", file=sys.stderr)

md = ["# Comparativo de tradução — Haiku × rota simple (consolidate-llm-on-openai, task 3.1)\n",
      f"Gerado em {time.strftime('%Y-%m-%d')}. 10 análises em inglês geradas pelo prompt de produção",
      "(`skills/analise/workout-analyzer/SKILL.md`, Sonnet 4.6, temp 0.2) a partir de treinos **sintéticos**.",
      "Cada campo traduzido com `prompts/translate-field-prompt.txt` e o perfil de rota do `application.yml`.",
      "As versões A, B e C são embaralhadas por caso; a chave está no fim. Leia antes de abrir a chave.\n",
      "**Como avaliar (CA2):** em cada caso, alguma versão perde sentido, número ou unidade, sai em inglês",
      "ou soa artificial para o coach? Anote a pior e a melhor.\n"]
md += linhas
md.append("\n## Verificação automática\n")
md.append('\n'.join(f"- {a}" for a in alertas) or "- nenhum campo vazio; nenhum número do original ausente.")
md.append("\n## Latência e tokens (4 chamadas sequenciais por análise)\n")
md.append("| modelo | latência média (s) | latência máx (s) | tokens in | tokens out |\n|---|---|---|---|---|")
for n in TRADUTORES:
    md.append(f"| {n} | {sum(lat[n]) / len(lat[n]):.1f} | {max(lat[n]):.1f} | {toks[n][0]} | {toks[n][1]} |")
md.append("\n<details><summary>Chave (abrir só depois de avaliar)</summary>\n\n| caso | | | |\n|---|---|---|---|")
md += chave
md.append("\n</details>\n")
saida.write_text('\n'.join(md))
print(f"gravado em {saida}", file=sys.stderr)
