package br.com.bolaoboladao.carteira.infrastructure.schedule;

import br.com.bolaoboladao.carteira.infrastructure.persistence.repository.PanacheOutboxEventRepository;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.quarkus.scheduler.Scheduled;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.jboss.logging.Logger;

@ApplicationScoped
public class OutboxRetryJob {

    private static final Logger LOG = Logger.getLogger(OutboxRetryJob.class);

    @Inject
    PanacheOutboxEventRepository outboxRepository;

    @Inject
    @Channel("wallet-events")
    Emitter<String> walletEventEmitter;

    @Scheduled(every = "10s")
    @WithTransaction
    public Uni<Void> retryOutboxEvents() {
        return outboxRepository.findAll().list()
                .flatMap(pendingEvents -> {
                    if (pendingEvents.isEmpty()) {
                        return Uni.createFrom().voidItem();
                    }

                    LOG.infof("Encontrados %d eventos no outbox para reprocessamento", pendingEvents.size());

                    return Multi.createFrom().iterable(pendingEvents)
                            .onItem().transformToUniAndConcatenate(event ->
                                    Uni.createFrom().completionStage(() -> walletEventEmitter.send(event.getPayload()))
                                            .flatMap(ignore -> {
                                                LOG.infof("Evento %s reprocessado e removido do outbox", event.getId());
                                                return outboxRepository.delete(event).replaceWithVoid();
                                            })
                                            .onFailure().recoverWithUni(e -> {
                                                LOG.errorf("Falha ao reenviar evento do outbox %s: %s", event.getId(), e.getMessage());
                                                return Uni.createFrom().voidItem();
                                            })
                            )
                            .collect().asList()
                            .replaceWithVoid();
                });
    }
}
