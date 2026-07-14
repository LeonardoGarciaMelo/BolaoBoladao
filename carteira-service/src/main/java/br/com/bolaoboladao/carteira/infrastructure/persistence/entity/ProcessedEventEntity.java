package br.com.bolaoboladao.carteira.infrastructure.persistence.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Data;

@Entity
@Table(name = "processed_event")
@Data
public class ProcessedEventEntity {
    @Id
    private UUID eventId;
    private String eventType;
    private LocalDateTime processedAt;
}
