#!/usr/bin/env bash
source "$(dirname "$0")/../lib.sh"
preparar

titulo "Bulkhead + células: lentidão numa célula não contamina as outras"
passo "célula 0 lenta (700ms); células 1 e 2 normais"
chaos '{"latenciaMs":700,"celulasAfetadas":[0]}'
carga 150 25 > /dev/null &
sleep 1.5
passo "uso dos bulkheads durante a carga"
status '[.bulkheads[] | {nome, emUso, maximo, rejeitadas}]'
wait
passo "resultado por célula"
status '[.bulkheads[] | {nome, rejeitadas}], [.circuitBreakers[] | {nome, estado}]'
chaos_reset
