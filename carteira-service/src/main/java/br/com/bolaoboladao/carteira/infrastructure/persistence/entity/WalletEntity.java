package br.com.bolaoboladao.carteira.infrastructure.persistence.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Data;

@Entity
@Table(name = "wallet")
@Data
public class WalletEntity {
    @Id
    private UUID id;
    private UUID userId;
}
