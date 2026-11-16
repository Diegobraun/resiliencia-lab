#!/usr/bin/env bash
source "$(dirname "$0")/../lib.sh"

titulo "Falha metastável: gatilho some, sistema não volta"
curl -s "$BASE/api/metastavel/comparar" | jq 'to_entries[] | {modo: .key, recuperou: .value.recuperou, ticksAteRecuperar: .value.ticksAteRecuperarAposGatilho, filaMaxima: .value.filaMaxima, amplificacao: .value.amplificacaoMaximaDeCarga, goodputFinal: .value.goodputMedioUltimos20Ticks, goodput: .value.graficoGoodput, fila: .value.graficoFila}'
