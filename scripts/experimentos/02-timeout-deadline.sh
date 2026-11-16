#!/usr/bin/env bash
source "$(dirname "$0")/../lib.sh"
preparar

titulo "Timeout e deadline propagation"
passo "core com 1200ms de latência; timeout por chamada é 800ms"
chaos '{"latenciaMs":1200}'
passo "cliente dá só 1000ms: gateway corta a chamada e não tenta de novo além do prazo"
transferir cliente-1 conta-1 conta-2 10 "$(uuidgen)" -H 'X-Request-Timeout-Ms: 1000'
sleep 1.5
passo "core descartou trabalho cujo deadline já tinha passado (não aplica nada):"
status '.coreSimulado'
passo "cliente pede só 20ms: recusado na entrada, sem gastar nada"
transferir cliente-1 conta-1 conta-2 10 "$(uuidgen)" -H 'X-Request-Timeout-Ms: 20'
chaos_reset
