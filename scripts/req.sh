#!/usr/bin/env bash
i="$1"
args=(-s -o /dev/null -w '%{http_code}\n' -X POST "$BASE/api/transferencias"
  -H 'Content-Type: application/json'
  -H "X-Cliente: ${CLIENTE_FIXO:-${PREFIXO:-cliente}-$(( i % ${CLIENTES:-50} ))}"
  -H "Idempotency-Key: $(uuidgen)")
if [ -n "${PRIORIDADE:-}" ]; then
  args+=(-H "X-Prioridade: $PRIORIDADE")
fi
curl "${args[@]}" -d "{\"contaOrigem\":\"${ORIGEM:-conta-1}\",\"contaDestino\":\"${DESTINO:-conta-2}\",\"valor\":1}"
