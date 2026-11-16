# resiliencia-lab

Laboratório de padrões de resiliência num cenário de gateway bancário: um gateway recebe transferências e fala com um core bancário lento e instável. Tudo roda numa aplicação Spring Boot só, sem Docker. O core, o control plane e o broker são simulados dentro dela, e o chaos pode ser ligado e desligado por API.

Os padrões foram escritos à mão, sem Resilience4j, para dar para ler o mecanismo inteiro em poucas linhas.

## Rodando

Pré-requisitos: Java 21+, Maven e `jq` (para os scripts).

```bash
mvn spring-boot:run
mvn test
```

Se a 8080 estiver ocupada:

```bash
mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=8090
export BASE=http://localhost:8090
```

Experimentos (um por padrão):

```bash
bash scripts/experimentos/01-idempotencia.sh
bash scripts/rodar-todos.sh
```

Painel de tudo: `GET /api/status` (breakers, bulkheads, retry budget, load shedding, shuffle shards, outbox, broker, config operacional, rollout, transferências por status, chaos ativo).

## Arquitetura

```
 cliente
   │  Idempotency-Key, X-Cliente, X-Prioridade, X-Request-Timeout-Ms
   ▼
┌───────────────────────────── gateway ──────────────────────────────┐
│ DeadlineFilter ─► LoadSheddingFilter ─► ShuffleShardFilter          │
│        │                                                            │
│        ▼                                                            │
│ TransferenciaController ─► IdempotenciaService ─► TransferenciaSaga │
│                                                     │        │      │
│                     ┌───────────────────────────────┘        │      │
│                     ▼                                        ▼      │
│              CoreGateway                         transferencia +    │
│  CelulaRouter ─► célula N:                       outbox (mesma tx)  │
│                  CircuitBreaker                          │          │
│                  Bulkhead                          OutboxRelay      │
│                  RetryPolicy + RetryBudget               │          │
│                  RolloutService (v1/v2)          BrokerSimulado ─►  │
│                                                  consumidor (dedup) │
│ ReconciliacaoService (agendada)                                     │
│ ControlPlaneSync ─► ConfigOperacionalHolder (memória + disco)       │
└─────────────────────────────────────────────────────────────────────┘
        │ HTTP real (JDK HttpClient), X-Celula, X-Deadline-Restante-Ms
        ▼
  /core/**  core simulado (idempotente por operacaoId) + chaos
  /control-plane/config  control plane simulado
  /chaos  injeção de falhas
```

O gateway chama o core por HTTP de verdade (para a própria porta), então timeouts, conexões e threads presas acontecem de fato.

### Funil de limites

Os limites são escalonados, do mais estreito para o mais largo:

| Camada | Limite | Protege contra |
|---|---|---|
| Shuffle shard por cliente | 2 workers × 4 = 8 em voo | um cliente monopolizar o gateway |
| Bulkhead por célula | 10 chamadas ao core | uma célula lenta prender todas as threads |
| Load shedding global | 30 em voo (BAIXA 15, NORMAL 25, CRITICA 30) | sobrecarga geral |
| Shuffle sharding total | 8 × 4 = 32 | — (fica acima do global de propósito) |

Se um limite interno for mais largo que um externo, ele nunca atua. Os experimentos 05, 06 e 07 só funcionaram depois desse ajuste.

## Fluxo de uma transferência

```
INICIADA ─débito─► DEBITADA ─crédito─► CONCLUIDA
   │                  │   │
   │ rejeitado        │   └ crédito recusado ─► COMPENSANDO ─estorno─► COMPENSADA
   ▼                  │                             └ incerto ─► COMPENSACAO_PENDENTE
REJEITADA             └ crédito incerto ─► CREDITO_INCERTO
   │ não enviado ─► FALHOU
   └ incerto ─► DEBITO_INCERTO
```

Cada chamada ao core tem um `operacaoId` estável (`{id}:D`, `{id}:C`, `{id}:E`) e o core é idempotente por ele. Repetir é sempre seguro, e é isso que permite retry, reconciliação e recuperação para frente.

A distinção central do projeto está em `ResultadoLancamento.Tipo`:

| Tipo | Significado | O que a saga faz |
|---|---|---|
| `SUCESSO` | core confirmou | segue |
| `REJEITADO` | 4xx: falha de negócio, definitiva | compensa ou rejeita |
| `NAO_ENVIADO` | breaker aberto, bulkhead cheio ou deadline esgotado antes de enviar | nada aconteceu no core, seguro falhar |
| `INCERTO` | timeout ou 5xx depois de enviar | **não sabe**, marca incerto e a reconciliação resolve |

Timeout não quer dizer que a operação falhou. Tratar INCERTO como falha gera débito sem crédito, e tratar como sucesso gera crédito sem débito.

## Os padrões

### 1. Timeout e deadline propagation
`padroes/Deadline.java`, `padroes/DeadlineContext.java`, `web/DeadlineFilter.java`, `gateway/CoreGateway.java#timeoutDaChamada`

- O cliente manda `X-Request-Timeout-Ms` (padrão 3000, teto 5000). Abaixo de 50ms, a requisição é recusada com 504 sem fazer nada.
- O timeout de cada chamada é `min(800ms, tempo restante)`. Por isso o segundo retry sai com timeout menor: ele usa o que sobrou do prazo.
- O gateway manda o tempo restante **relativo** em `X-Deadline-Restante-Ms`, como o `grpc-timeout`, para não depender do relógio de outra máquina. O core verifica o prazo antes de aplicar e descarta o trabalho se o chamador já desistiu (`coreSimulado.descartadosPorDeadline`).
- Garantia que a reconciliação usa: passado o deadline máximo, nenhuma requisição antiga ainda pode ser aplicada.

Experimento: `02-timeout-deadline.sh`.

### 2. Retry com backoff exponencial e jitter
`padroes/RetryPolicy.java`

- Usa *full jitter*: espera aleatória entre 0 e `min(teto, base × 2^(n-1))`. Sem jitter, todos os clientes repetem no mesmo instante e criam picos sincronizados.
- Só repete `FalhaChamadaCore.Transitoria` (timeout ou 5xx). Erro de negócio, breaker aberto e bulkhead cheio não são repetidos.
- Não dorme se o backoff não cabe no deadline.

### 3. Retry budget
`padroes/RetryBudget.java`

- Cada requisição nova deposita 0,1 token e cada retry gasta 1. No regime estável, retries ficam limitados a cerca de 10% do tráfego.
- Sem budget, com 3 tentativas uma falha geral **triplica** a carga no core no pior momento. Esse é o mecanismo da falha metastável (padrão 15).

Experimento: `03-retry-budget.sh` (30% de erro, `retriesNegados` sobe).

### 4. Circuit breaker
`padroes/CircuitBreaker.java`

- Janela deslizante por contagem (20 chamadas). Abre com pelo menos 10 chamadas e taxa de falha ≥ 50%.
- Fica `ABERTO` por 5s e falha na hora, sem tocar no core. Depois vai para `MEIO_ABERTO`, deixa passar 3 chamadas de teste e fecha se todas passarem. Uma falha reabre.
- Um breaker **por célula**, então a falha de uma célula não abre o circuito das outras.
- Rejeição do breaker vira `NAO_ENVIADO`.

Experimento: `04-circuit-breaker.sh`.

### 5. Bulkhead
`padroes/Bulkhead.java`

- Semáforo com `tryAcquire` sem espera, 10 chamadas simultâneas por célula. A requisição não fica na fila: se não há vaga, falha rápido.
- Sem bulkhead, uma célula com 700ms de latência prende todas as threads do Tomcat e derruba as outras células junto.

Experimento: `05-bulkhead-celulas.sh` (célula 0 lenta: só o bulkhead dela enche).

### 6. Load shedding
`web/LoadSheddingFilter.java`

- Conta as requisições em voo. Cada prioridade pode usar uma fração do limite: BAIXA 50%, NORMAL 85%, CRITICA 100%.
- Responde 503 com `Retry-After`. Rejeitar cedo custa microssegundos, aceitar e dar timeout custa o trabalho todo.
- `/api/status` fica fora do shedding, porque observabilidade não pode cair junto com a sobrecarga.

Experimento: `06-load-shedding.sh` (CRITICA passa 100%, BAIXA é descartada).

### 7. Backpressure
`outbox/OutboxRelay.java`, `outbox/BrokerSimulado.java`

- O broker tem fila limitada (100) e o relay usa `offer`, que não bloqueia. Com a fila cheia, o relay **para** e tenta no próximo ciclo (`pausasPorBackpressure`).
- O excesso fica no banco (outbox), não na memória. Um consumidor lento atrasa a entrega, mas não derruba o produtor com `OutOfMemoryError`.

Experimento: `09-outbox-backpressure.sh`.

### 8. Idempotency key
`idempotencia/IdempotenciaService.java`, tabela `idempotencia`

- A chave é `(cliente, Idempotency-Key)`. Funciona assim:
  1. `INSERT EM_ANDAMENTO` (a PK garante exclusividade).
  2. Executa a operação.
  3. Grava `CONCLUIDA` com status e corpo da resposta.
- Mesma chave com mesmo conteúdo devolve a resposta gravada, com `Idempotent-Replayed: true`.
- Mesma chave com outro conteúdo devolve 422. A comparação usa o hash da "impressão digital" (`100` e `100.00` são iguais).
- Mesma chave ainda em andamento devolve 409 com `Retry-After`.
- A trava `EM_ANDAMENTO` vence em 30s, para o caso de o processo morrer no meio.
- `FALHOU` não é gravado, porque nada aconteceu e o cliente pode tentar com a mesma chave. Resultado incerto (202) é gravado.
- Existem duas camadas de idempotência: cliente → gateway (Idempotency-Key) e gateway → core (`operacaoId`). As duas são necessárias.

Experimento: `01-idempotencia.sh`.

### 9. Outbox pattern
`outbox/OutboxRepository.java` (`Propagation.MANDATORY`), `transferencia/TransferenciaSaga.java#mudar`

- Cada mudança de status grava a transferência **e** o evento na mesma transação. Não existe caso de "status mudou mas o evento se perdeu", nem o contrário.
- `OutboxRepository.registrar` exige uma transação aberta (`MANDATORY`), então não dá para gravar o evento fora dela por engano.
- A entrega é *at-least-once*. O chaos `taxaFalhaRelay` simula crash entre publicar e marcar como publicado, o evento sai duas vezes e o consumidor descarta pelo `eventoId` (`duplicadosIgnorados`).

### 10. Saga e compensação
`transferencia/TransferenciaSaga.java`

- São três passos locais: débito, crédito e, se preciso, estorno. Nenhuma transação distribuída.
- **Compensa só quando a falha é definitiva** (crédito `REJEITADO`). Se o crédito foi `NAO_ENVIADO`, a saga continua em `DEBITADA` e a reconciliação tenta o crédito de novo: é recuperação para frente. Estornar a cada falha transitória gera estornos desnecessários e confunde o extrato do cliente.
- Estorno com resultado incerto fica `COMPENSACAO_PENDENTE`. Estorno recusado fica `COMPENSACAO_FALHOU` e precisa de uma pessoa.
- As transições usam `UPDATE ... WHERE status = :esperado` (lock otimista). Se a requisição e a reconciliação mexem ao mesmo tempo, só uma vence.

Experimento: `08-saga-compensacao.sh`.

### 11. Reconciliação
`reconciliacao/ReconciliacaoService.java`

- Roda a cada 15s e pega transferências em estados intermediários paradas há mais de 8s. Os 8s ficam acima do deadline máximo (5s), o que garante que nenhuma requisição antiga ainda está para chegar no core.
- Pergunta ao core pelo `operacaoId`: `EXISTE` avança, `NAO_EXISTE` refaz ou falha, `DESCONHECIDO` (core fora ou breaker aberto) **não mexe**.
- É a fonte de verdade final. Retry, breaker e timeout reduzem a incerteza, mas só a reconciliação elimina.

Experimento: `10-reconciliacao.sh` (core perde 50% das respostas, a reconciliação conclui todas).

### 12. Static stability
`estabilidade/ControlPlaneSync.java`, `estabilidade/ConfigOperacionalHolder.java`

- A config operacional (limite por transferência, clientes fixados em célula) vem do control plane a cada 3s, em background.
- O caminho da requisição **só lê da memória** e nunca chama o control plane. Se o control plane cair, o data plane continua com a última config boa (`falhasConsecutivas` sobe, nada quebra).
- A config é gravada em disco (`target/config-cache.json`, escrita atômica). A aplicação reiniciada com o control plane fora sobe com o cache (`origem: CACHE_DISCO`). Sem cache, usa o padrão embarcado.
- É o contrário do Facebook BGP 2021 e do AWS us-east-1: ali o plano de controle virou dependência dura do plano de dados.

Experimento: `11-static-stability.sh`.

### 13. Cell-based architecture
`celulas/CelulaRouter.java`

- Cada cliente cai numa célula (`hash % 3`) e cada célula tem breaker, bulkhead e, no chaos, falhas próprias. Falha numa célula afeta ~1/3 dos clientes.
- `clientesFixados` no control plane permite mover um cliente de célula sem deploy (migração, isolamento de cliente problemático).
- Em produção, célula é uma pilha inteira (instâncias, banco, fila). Aqui é só o caminho até o core, mas o raio de impacto se comporta igual.

Experimento: `05-bulkhead-celulas.sh`, teste `falhaEmUmaCelulaNaoAfetaAsOutras`.

### 14. Shuffle sharding
`shuffle/ShuffleSharding.java`, `web/ShuffleShardFilter.java`

- São 8 workers e cada cliente recebe uma combinação determinística de 2 (28 combinações possíveis). A requisição usa o primeiro worker livre do seu shard.
- Um cliente barulhento satura só os 2 workers dele e recebe 429. Outro cliente só fica totalmente bloqueado se tiver **exatamente** o mesmo par, ~3,6% (1/28). Com sharding simples (4 grupos fixos de 2), seriam 25%.
- `GET /api/shuffle-sharding/analise?clientes=10000` faz a conta.

Experimento: `07-shuffle-sharding.sh`.

### 15. Falha metastável
`metastavel/SimuladorMetastavel.java`

Esta é uma simulação discreta, sem HTTP, porque reproduzir de verdade exige carga sustentada. O cenário: chegam 80 req/tick, a capacidade é 100, o timeout do cliente é 10 ticks e cada cliente tenta até 3 vezes. Um gatilho derruba a capacidade para 30 entre os ticks 50 e 70.

```
ingênuo   goodput ▇▇▇▇▇▇▇▇▇▇▇▇▇▇▇▇▇▇▇▇▇▇▇▇▇▃▃▃▃▃▃▃▃▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁
          fila    ▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▂▂▂▂▂▂▂▂▂▂▂▃▃▃▃▃▃▃▃▃▃▄▄▄▄▄▄▄▄▄▄▅▅▅▅▅▅▅▅▅▅▆▆▆▆▆▆▆▆▆▇▇▇▇▇▇▇▇▇▇█████
mitigado  goodput ▇▇▇▇▇▇▇▇▇▇▇▇▇▇▇▇▇▇▇▇▇▇▇▇▇▃▃▃▃▃▃▃▃▃▃███████████▇▇▇▇▇▇▇▇▇▇▇▇▇▇▇▇▇▇▇▇▇▇▇▇▇▇▇▇▇▇▇▇▇▇▇▇▇▇▇▇▇▇▇▇▇
          fila    ▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▂▃▅▆████▇▇▇▆▆▅▅▄▃▃▂▂▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁
```

- **Ingênuo**: depois do gatilho, a fila está cheia de requisições cujo cliente já desistiu. O servidor gasta a capacidade inteira nelas (trabalho desperdiçado) enquanto os retries triplicam a carga. O gatilho some e o sistema **não volta**: goodput zero e fila crescendo para sempre.
- **Mitigado**: o servidor descarta de graça o que já expirou (deadline propagation), os retries ficam limitados por budget e a fila é limitada (load shedding). Recupera assim que o gatilho some.
- A lição: o problema não é o gatilho. É o laço de realimentação que se sustenta sozinho. Reiniciar ou escalar não resolve se o laço continua.

Experimento: `12-metastavel.sh` ou `GET /api/metastavel/simular?mitigado=false&detalhado=true`.

### 16. Canary e staged rollout
`rollout/RolloutService.java`, `rollout/Versao.java`

- A v2 do cliente do core (`/core/v2/lancamentos`) sai em estágios de 5%, 25%, 50% e 100%, com 10s cada.
- O cliente cai na v2 por `hash(cliente) < percentual`, então é *sticky*: o mesmo cliente sempre na mesma versão.
- A cada 1s compara as taxas de erro. Com v2 > v1 + 5 p.p. e pelo menos 10 amostras, faz **rollback automático** para 0%. Avança de estágio só com tempo cumprido e amostras suficientes.
- É o que faltou no CrowdStrike 2024 e na Cloudflare 2019/2025: mudança global de uma vez, sem canário.

Experimento: `13-canary.sh` (v2 quebrada dá rollback em ~2s, v2 saudável vai até 100%).

### 17. Chaos engineering
`chaos/Chaos.java`, `chaos/ChaosService.java`, `scripts/experimentos/`

Cada script segue a estrutura de experimento:

1. estado estável (`/api/status`);
2. hipótese ("célula 0 lenta não afeta 1 e 2");
3. injeção (`PUT /chaos`);
4. observação;
5. reversão (`DELETE /chaos`).

| Campo | Efeito |
|---|---|
| `latenciaMs` | atraso no core |
| `taxaErro` | 500 antes de aplicar (nada aconteceu) |
| `taxaFalhaAposAplicar` | aplica e devolve 500 (o caso perigoso) |
| `coreForaDoAr` | 503 |
| `celulasAfetadas` | limita os efeitos acima a certas células (vazio = todas) |
| `v2Quebrada` | `/core/v2` sempre 500 |
| `controlPlaneForaDoAr` | control plane 503 |
| `consumidorLentoMs` | atraso por evento no consumidor do broker |
| `taxaFalhaRelay` | relay "morre" entre publicar e marcar |

`PUT /chaos` aceita campos parciais e recusa campos desconhecidos (um erro de digitação não pode virar um experimento que não testa nada).

## Endpoints

| Método | Caminho | Para quê |
|---|---|---|
| POST | `/api/transferencias` | transferência (headers `Idempotency-Key` e `X-Cliente` obrigatórios) |
| GET | `/api/transferencias/{id}` | estado atual |
| GET | `/api/status` | painel de todos os mecanismos |
| POST | `/api/reconciliacao?idadeMinimaMs=` | reconciliação manual |
| GET | `/api/reconciliacao/ultima` | último relatório |
| GET/POST | `/api/rollout`, `/iniciar`, `/abortar` | canary |
| GET | `/api/metastavel/simular`, `/comparar` | simulação |
| GET | `/api/shuffle-sharding/clientes/{c}`, `/analise` | shards |
| GET/PUT/DELETE | `/chaos` | injeção de falhas |
| GET/PUT | `/control-plane/config` | control plane simulado |
| POST/GET | `/core/lancamentos`, `/core/v2/lancamentos`, `/core/contas/{c}`, `/core/reset` | core simulado |

Contas do core: `conta-1` a `conta-5` (1.000.000,00 cada) e `conta-pobre` (10,00). `conta-inexistente` serve para disparar a compensação.

## Simplificações (o que muda em produção)

- Tudo num processo só. Em produção, gateway, core e control plane são serviços separados, e o core e o gateway não dividem o pool do Tomcat.
- H2 em memória: reiniciar apaga transferências, outbox e chaves de idempotência. O cache de config em disco sobrevive.
- Os contadores de `/api/status` são acumulados desde o start. Em produção seriam métricas (Micrometer/Prometheus) com janela de tempo.
- A reconciliação não tem limite de tentativas nem fila de revisão humana.
- O breaker conta por chamadas, não por tempo. O load shedding é por concorrência fixa, não adaptativo (Netflix concurrency-limits, CoDel).
- O broker é uma fila em memória. No lugar dele entraria um Kafka, e o relay usaria polling com `FOR UPDATE SKIP LOCKED` ou CDC (Debezium) para várias instâncias.

## Para estudar junto

| Padrão | Leitura | Caso real |
|---|---|---|
| Timeout, retry, jitter | Amazon Builders' Library: *Timeouts, retries and backoff with jitter* | AWS Kinesis 2020 |
| Retry budget, metastável | Bronson et al., *Metastable Failures in Distributed Systems* (HotOS 2021) | AWS DynamoDB DNS 2025, Roblox 2021 |
| Circuit breaker, bulkhead | Nygard, *Release It!* | — |
| Load shedding | Google SRE Book, *Handling Overload* e *Addressing Cascading Failures* | Slack jan/2021 |
| Idempotência | Stripe, *Designing robust and predictable APIs with idempotency*; Builders' Library: *Making retries safe with idempotent APIs* | — |
| Outbox, saga | Kleppmann, *Designing Data-Intensive Applications* | — |
| Reconciliação | — | TSB Bank 2018 |
| Static stability, células | Builders' Library: *Static stability using Availability Zones* | Facebook BGP 2021 |
| Shuffle sharding | Builders' Library: *Workload isolation using shuffle-sharding* | — |
| Canary | — | CrowdStrike 2024, Cloudflare 2019 e 2025, Knight Capital 2012 |
| Chaos | Rosenthal & Jones, *Chaos Engineering*; Richard Cook, *How Complex Systems Fail* | — |
