package br.com.bolaoboladao.carteira.infrastructure.persistence.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "processed_event")
@Data
public class ProcessedEventEntity {
    @Id
    private UUID eventId;
    private String eventType;
    private LocalDateTime processedAt;
}
