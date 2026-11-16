#!/usr/bin/env bash
source "$(dirname "$0")/../lib.sh"
preparar

titulo "Outbox + backpressure"
passo "consumidor lento (50ms/evento = 20/s) e relay falhando 3% entre publicar e marcar"
chaos '{"consumidorLentoMs":50,"taxaFalhaRelay":0.03}'
carga 150 20 > /dev/null
passo "fila do broker cheia -> relay pausa; eventos esperam no banco, não na memória"
status '{outbox, broker}'
sleep 10
passo "10s depois: drenando; duplicados (publicados 2x) foram ignorados pelo consumidor"
status '{outbox, broker}'
chaos_reset
