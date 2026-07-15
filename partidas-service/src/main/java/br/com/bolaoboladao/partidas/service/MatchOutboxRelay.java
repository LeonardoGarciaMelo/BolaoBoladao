package br.com.bolaoboladao.partidas.service;

import br.com.bolaoboladao.partidas.domain.MatchEvent;
import br.com.bolaoboladao.partidas.repository.MatchEventRepository;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.scheduler.Scheduled;
import io.smallrye.reactive.messaging.kafka.api.OutgoingKafkaRecordMetadata;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.Metadata;
import org.jboss.logging.Logger;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@ApplicationScoped
public class MatchOutboxRelay {

    private static final Logger LOG = Logger.getLogger(MatchOutboxRelay.class);

    @Inject
    MatchEventRepository matchEventRepository;

    @Inject
    @Channel("match-events")
    Emitter<MatchDomainEvent> emitter;

    public record ScoreDto(Integer team_home, Integer team_away) {}
    public record MatchDomainEvent(
            String event_id, // Identificador global e estável para deduplicação no consumidor
            UUID match_id,
            String event_type,
            String team_home,
            String team_away,
            OffsetDateTime scheduled_start,
            ScoreDto score,
            OffsetDateTime occurred_at,
            UUID actor_id,
            String reason
    ) {}

    @Scheduled(every = "2s", delayed = "5s", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    public void relayEvents() {
        // 1. Busca eventos pendentes no banco de dados (fora da transação principal para não bloquear)
        List<MatchEvent> pendingEvents = QuarkusTransaction.requiringNew().call(() ->
                matchEventRepository.find("published = false order by id").list()
        );

        if (pendingEvents.isEmpty()) {
            return;
        }

        LOG.infof("Relay: processando %d eventos pendentes no outbox", pendingEvents.size());

        for (MatchEvent event : pendingEvents) {
            try {
                // 2. Mapeamento para o DTO do evento de domínio
                MatchDomainEvent payload = toDomainEvent(event);

                // 3. Monta o metadado Kafka para usar a partida como chave (garante ordenação no mesmo tópico/partição)
                OutgoingKafkaRecordMetadata<String> metadata = OutgoingKafkaRecordMetadata.<String>builder()
                        .withKey(event.match.id.toString())
                        .build();

                CompletableFuture<Void> acked = new CompletableFuture<>();

                // Envia a mensagem interceptando o Ack e Nack do Kafka
                emitter.send(Message.of(payload, Metadata.of(metadata))
                        .withAck(() -> {
                            acked.complete(null);
                            return CompletableFuture.completedFuture(null);
                        })
                        .withNack(throwable -> {
                            acked.completeExceptionally(throwable);
                            return CompletableFuture.completedFuture(null);
                        })
                );

                // Espera a confirmação de recebimento (Ack) do Kafka por até 5 segundos
                acked.get(5, TimeUnit.SECONDS);
                LOG.infof("Relay: Evento %d (%s) publicado no Kafka com sucesso", event.id, event.eventType);

                // 4. Se o Kafka confirmou, atualizamos no banco
                QuarkusTransaction.requiringNew().run(() -> {
                    MatchEvent me = matchEventRepository.findById(event.id);
                    if (me != null) {
                        me.published = true;
                    }
                });

            } catch (Exception e) {
                LOG.errorf(e, "Relay: Falha ao processar e publicar o evento %d. Parando lote para preservar ordem.", event.id);
                // Interrompe o loop no primeiro erro para preservar a ordem sequencial dos eventos
                break;
            }
        }
    }

    static MatchDomainEvent toDomainEvent(MatchEvent event) {
        return new MatchDomainEvent(
                event.match.id + ":" + event.id,
                event.match.id,
                event.eventType.name(),
                event.match.teamHome.name,
                event.match.teamAway.name,
                event.match.start,
                new ScoreDto(event.teamHomeScoreAtEvent, event.teamAwayScoreAtEvent),
                event.occurredAt,
                event.actorId,
                event.reason
        );
    }
}
