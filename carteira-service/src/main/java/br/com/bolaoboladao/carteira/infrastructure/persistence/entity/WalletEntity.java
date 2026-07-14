package br.com.bolaoboladao.carteira.infrastructure.persistence.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.util.UUID;

@Entity
@Table(name = "wallet")
@Data
public class WalletEntity {
    @Id
    private UUID id;
    private UUID userId;
}
