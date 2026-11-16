#!/usr/bin/env bash
source "$(dirname "$0")/../lib.sh"
preparar

titulo "Circuit breaker: falhar rápido em vez de esperar timeout"
passo "core fora do ar e lento"
chaos '{"coreForaDoAr":true}'
carga 60 10
passo "breakers abertos: chamadas recusadas sem tocar no core"
status '[.circuitBreakers[] | {nome, estado, rejeitadas, aberturas}]'
passo "core volta; depois de 5s o breaker vai para MEIO_ABERTO e testa com poucas chamadas"
chaos_reset
sleep 6
carga 30 3
status '[.circuitBreakers[] | {nome, estado}]'
