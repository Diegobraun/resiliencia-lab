#!/usr/bin/env bash
source "$(dirname "$0")/../lib.sh"
preparar

titulo "Reconciliação: resolver resultados incertos"
passo "core aplica mas perde a resposta em 50% das vezes"
chaos '{"taxaFalhaAposAplicar":0.5}'
carga 40 5
chaos_reset
status '.transferenciasPorStatus'
passo "breakers abriram com tantas falhas; enquanto abertos a reconciliação responde DESCONHECIDO e não mexe em nada"
status '[.circuitBreakers[] | {nome, estado}]'
sleep 6
passo "reconciliação (idadeMinimaMs=0 só para a demo; o padrão espera passar o deadline máximo)"
curl -s -X POST "$BASE/api/reconciliacao?idadeMinimaMs=0" | jq '{analisadas, resolvidas, acoes: .acoes[0:5]}'
status '.transferenciasPorStatus'
