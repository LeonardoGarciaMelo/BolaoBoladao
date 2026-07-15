package br.com.bolaoboladao.carteira.domain.repository;

import br.com.bolaoboladao.carteira.domain.model.Ledger;
import io.smallrye.mutiny.Uni;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LedgerRepository {
    Uni<Void> save(Ledger ledger);

    Uni<List<Ledger>> findByWalletIdAndDate(UUID walletId, LocalDate date);

    Uni<List<Ledger>> findByWalletIdAndDateBetween(UUID walletId, LocalDate startDate, LocalDate endDate);

    Uni<List<Ledger>> findByWalletIdPaged(UUID walletId, int page, int size);

    Uni<Long> countByWalletId(UUID walletId);

    Uni<Ledger> findByIdempotencyKey(String idempotencyKey);

    Uni<Void> lockIdempotencyKey(String idempotencyKey);

    Uni<List<Ledger>> findAdminCredits(int offset, int size, LocalDateTime until);

    Uni<Long> countAdminCredits(LocalDateTime until);
}
