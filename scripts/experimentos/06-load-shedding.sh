#!/usr/bin/env bash
source "$(dirname "$0")/../lib.sh"
preparar

titulo "Load shedding por prioridade"
passo "core lento (600ms) para acumular requisições em voo"
chaos '{"latenciaMs":600}'
passo "BAIXA com 25 em paralelo + CRITICA com 10 em paralelo; BAIXA só pode ocupar 50% do limite (15 de 30)"
TMP=$(mktemp -d)
PREFIXO=baixa PRIORIDADE=BAIXA carga 120 25 > "$TMP/baixa" &
PREFIXO=critica PRIORIDADE=CRITICA carga 60 10 > "$TMP/critica" &
wait
echo "   BAIXA:"; cat "$TMP/baixa"
echo "   CRITICA:"; cat "$TMP/critica"
status '.loadShedding'
chaos_reset
