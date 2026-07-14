# Guia de Apresentação — Bounded Context de Partidas (Bolão Boladão)

Este documento serve como um roteiro e roteiro de apoio (slide a slide) para você ler e guiar sua fala durante a banca. Ele está dividido entre o que colocar visualmente (em slides ou num documento compartilhado) e o que você deve falar e demonstrar no código/terminal.

---

## 📌 Slide 1: Visão Geral e Arquitetura de Domínio (DDD)
### Visual do Slide:
* **Título:** Arquitetura do Microsserviço de Partidas
* **Pontos Chave:**
  * Domínio do negócio: Gestão de times, partidas e eventos do jogo.
  * Bounded Context isolado: Sem joins de banco ou dependência de dados direta.
  * Banco de dados próprio: PostgreSQL segregado (`partidas_db`).
  * Stack Técnica: Quarkus 3 (Java 21), Hibernate Panache e Flyway.

### 🎙️ O que falar (Roteiro):
> *"Olá a todos. Na nossa plataforma de Bolão (o Bolão Boladão), dividimos o sistema em 4 microsserviços usando DDD para evitar acoplamento e garantir escalabilidade. Eu fiquei encarregado de implementar o serviço de **Partidas**, que é a fonte da verdade sobre quais jogos existem, seus horários e placares. O serviço é totalmente isolado: tem seu próprio banco de dados Postgres e se comunica com os outros contextos de forma assíncrona."*

---

## 📌 Slide 2: Ciclo de Vida da Partida e Validação de Estado
### Visual do Slide:
* **Título:** Máquina de Estados da Partida
* **Fluxograma:**
  * `SCHEDULED` (Agendado) ──[ iniciar ]──▶ `IN_PROGRESS` (Em Andamento) ──[ encerrar ]──▶ `FINISHED` (Encerrado)
  * Evento de gol (HOME / AWAY) permitido apenas no estado `IN_PROGRESS`.
* **Segurança:**
  * Validações estritas de estado no domínio para evitar falhas de consistência.
  * Mapeamento de erros de negócio para status HTTP adequados (ex: `409 Conflict`).

### 🎙️ O que falar (Roteiro):
> *"Para garantir a consistência do negócio, implementamos uma máquina de estados rígida para a partida. Um jogo não pode receber gols se não estiver 'Em Andamento', e não pode ser iniciado se já estiver encerrado. Essas regras rodam dentro de transações locais ACID. Caso alguma regra de negócio seja violada, o serviço lança exceções de domínio mapeadas para códigos REST adequados, como o HTTP 409 Conflict."*

---

## 📌 Slide 3: Alta Performance com Cache-Aside (Redis)
### Visual do Slide:
* **Título:** Performance e Alívio do Banco de Dados
* **Arquitetura de Cache:**
  * Padrão **Cache-Aside** (Lazy Loading) com Redis para buscas de detalhes da partida (`GET /partidas/{id}`).
  * **Invalidação Ativa:** Escritas no banco (gol, início, fim) disparam evicção imediata da chave no Redis.
  * **Prevenção de Cache Stampede:** TTL dinâmico com Jitter (variação de tempo aleatória) para evitar expiração concorrente de chaves quentes.

### 🎙️ O que falar (Roteiro):
> *"Em dias de jogos importantes, a consulta de detalhes da partida tem um volume de leitura gigantesco. Para aliviar a carga no Postgres, implementamos **Cache-Aside** com Redis. O cache é populado sob demanda. Quando um gol acontece ou a partida muda de status, limpamos o cache de forma ativa (`evict`) para garantir consistência. Além disso, adicionamos um fator aleatório no TTL (Jitter) para que as chaves não expirem todas juntas, prevenindo a queda do banco por concorrência."*

---

## 📌 Slide 4: A Decisão de Ouro: Não Cachear o 404
### Visual do Slide:
* **Título:** Tratamento de Ausência Transitória no Cache
* **O Problema:** Cachear respostas 404 (recurso não encontrado) cria inconsistências graves quando o recurso é criado logo em seguida (o cache serve 404 desatualizado até o TTL expirar).
* **A Solução:** O fluxo de cache é interrompido em caso de exceção de não-encontrado. Apenas registros de partidas existentes são salvos no cache.

### 🎙️ O que falar (Roteiro):
> *"Um detalhe arquitetural sênior que aplicamos foi a decisão de **nunca cachear respostas 404**. Se um cliente consulta um ID de partida inexistente, respondemos com erro, mas não salvamos esse 'nada' no cache. Se salvássemos, no momento em que a partida fosse inserida no banco, o cache continuaria respondendo 404 por minutos, gerando reclamações e chamados de suporte."*

---

## 📌 Slide 5: Integração Assíncrona e Resiliente (Transactional Outbox)
### Visual do Slide:
* **Título:** Comunicação Confiável com Bounded Contexts
* **O Problema:** Enviar mensagens ao Kafka diretamente de controllers HTTP gera perda de dados se o broker estiver instável ou lento.
* **A Solução:** Padrão **Transactional Outbox**.
  * Os eventos são salvos na tabela `match_event` sob a mesma transação JTA da partida (se um falhar, tudo sofre rollback).
  * Um scheduler assíncrono (`MatchOutboxRelay`) processa a fila de outbox.
  * Garantia **At-Least-Once** com validação de recebimento (Ack) do Kafka.
  * Ordenação garantida por partição usando o ID da partida como chave.

### 🎙️ O que falar (Roteiro):
> *"Os outros serviços (como Apostas e Carteira) precisam reagir aos eventos de partida para encerrar palpites e pagar prêmios. Para integrar os serviços de forma assíncrona sem risco de perda de mensagens, usamos o padrão **Transactional Outbox**. Em vez de publicar direto no Kafka no meio da requisição REST, nós salvamos o evento na tabela `match_event` na mesma transação atômica do banco. Um processo em background lê essa tabela e publica no Kafka de forma resiliente, marcando como publicado apenas ao receber a confirmação de recebimento do broker."*

---

## 📌 Slide 6: Roteiro da Demonstração Prática (Live Demo)
### Visual do Slide:
* **Título:** Demonstração Prática e Evidências
* **Passos da Demo:**
  1. Subir containers via `docker compose up --build`.
  2. Criar uma nova partida (`POST /partidas`).
  3. Consultar a partida (Gerar *Cache Miss* e popular o Redis).
  4. Consultar novamente (Gerar *Cache Hit* instantâneo).
  5. Iniciar a partida (Evicção automática no Redis + gravação do evento no banco).
  6. Registrar um gol (Disparo de evento Kafka capturado no console + coluna `published` atualizada para true).

### 🎙️ O que falar (Roteiro):
> *"Vou mostrar agora na prática como tudo isso funciona. Já temos os containers do Postgres, Redis, Kafka e o nosso serviço de partidas rodando no Docker. Ao fazer a chamada de consulta, vemos o log de Miss e Hit do Redis. Ao dar o início da partida, o cache é invalidado e o evento é publicado no Kafka com segurança, onde os outros microsserviços do grupo poderão consumi-lo e agir de forma idempotente usando a chave do evento."*

---

## 📌 Slide 7: Testes Automatizados e Conclusão
### Visual do Slide:
* **Título:** Testabilidade e Próximos Passos
* **Testes de Integração:**
  * Uso de **Testcontainers** e **REST Assured** para simular o Postgres local em ambiente isolado.
  * Caso de teste específico para validar se o Cache e o Outbox Relay cooperam corretamente.
* **Próximos Passos:**
  * Integração do tópico `match-events` com o consumidor do serviço de Apostas (a ser feito pelo restante do grupo).

### 🎙️ O que falar (Roteiro):
> *"Para garantir que tudo funcione de forma confiável antes de enviar para produção, escrevemos testes de integração usando REST Assured e Testcontainers, simulando cenários de cache-aside e processamento de outbox. A base de partidas está concluída, documentada via ADRs e pronta para que meus colegas de grupo integrem os serviços de Apostas, Usuários e Carteiras consumindo nossos eventos do Kafka. Obrigado!"*
