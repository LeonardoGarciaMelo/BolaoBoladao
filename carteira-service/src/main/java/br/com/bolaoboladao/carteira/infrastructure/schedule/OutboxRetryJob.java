package br.com.bolaoboladao.carteira.infrastructure.schedule;

import br.com.bolaoboladao.carteira.infrastructure.persistence.entity.OutboxEventEntity;
import br.com.bolaoboladao.carteira.infrastructure.persistence.repository.PanacheOutboxEventRepository;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.List;
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
    @Transactional
    public void retryOutboxEvents() {
        List<OutboxEventEntity> pendingEvents = outboxRepository.findAll().list();
        if (pendingEvents.isEmpty()) {
            return;
        }

        LOG.infof("Encontrados %d eventos no outbox para reprocessamento", pendingEvents.size());

        for (OutboxEventEntity event : pendingEvents) {
            try {
                // Tenta reenviar o payload diretamente para o tópico
                walletEventEmitter.send(event.getPayload());
                // Se não lançou exceção (circuit breaker não abriu), removemos do outbox
                outboxRepository.delete(event);
                LOG.infof("Evento %s reprocessado e removido do outbox", event.getId());
            } catch (Exception e) {
                LOG.errorf("Falha ao reenviar evento do outbox %s: %s", event.getId(), e.getMessage());
                // Interrompe o loop para não bombardear o Kafka se ele ainda estiver fora
                break;
            }
        }
    }
}
