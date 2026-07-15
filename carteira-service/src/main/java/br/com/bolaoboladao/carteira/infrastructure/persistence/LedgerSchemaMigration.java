package br.com.bolaoboladao.carteira.infrastructure.persistence;

import io.quarkus.runtime.StartupEvent;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.pgclient.PgPool;
import io.vertx.mutiny.sqlclient.SqlConnection;
import io.vertx.mutiny.sqlclient.Tuple;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

@ApplicationScoped
public class LedgerSchemaMigration {
    @Inject PgPool client;

    void migrate(@Observes StartupEvent ignored) {
        client.withTransaction(connection -> connection.query("""
                        CREATE TABLE IF NOT EXISTS app_schema_migration (
                            version VARCHAR(100) PRIMARY KEY,
                            applied_at TIMESTAMPTZ NOT NULL DEFAULT now()
                        )
                        """).execute()
                .flatMap(ignoredResult -> connection.query(
                        "SELECT pg_advisory_xact_lock(hashtextextended('carteira-schema-migration', 0))").execute())
                .flatMap(ignoredResult -> apply(connection, "001-ledger-reasons", """
                        ALTER TABLE ledger DROP CONSTRAINT IF EXISTS ledger_reason_check;
                        ALTER TABLE ledger ADD CONSTRAINT ledger_reason_check
                        CHECK (reason IN ('WIN','DEPOSIT','BET','WITHDRAW','ADMIN_CREDIT','BET_REFUND'))
                        """))
                .flatMap(ignoredResult -> apply(connection, "002-wallet-user-unique", """
                        DO $$ BEGIN
                            IF NOT EXISTS (
                                SELECT 1 FROM pg_constraint WHERE conname = 'uq_wallet_user_id'
                            ) THEN
                                ALTER TABLE wallet ADD CONSTRAINT uq_wallet_user_id UNIQUE (userid);
                            END IF;
                        END $$
                        """))
                .flatMap(ignoredResult -> apply(connection, "003-deposit-requests", """
                        CREATE TABLE IF NOT EXISTS deposit_request (
                            id UUID PRIMARY KEY,
                            user_id UUID NOT NULL,
                            amount_cents BIGINT NOT NULL,
                            status VARCHAR(20) NOT NULL,
                            idempotency_key VARCHAR(200) NOT NULL,
                            provider_charge_id UUID,
                            checkout_url VARCHAR(2048),
                            expires_at TIMESTAMPTZ,
                            created_at TIMESTAMPTZ NOT NULL,
                            updated_at TIMESTAMPTZ NOT NULL,
                            confirmed_at TIMESTAMPTZ
                        );
                        DO $$ BEGIN
                            IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'deposit_amount_positive') THEN
                                ALTER TABLE deposit_request ADD CONSTRAINT deposit_amount_positive CHECK (amount_cents > 0);
                            END IF;
                            IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'deposit_status_check') THEN
                                ALTER TABLE deposit_request ADD CONSTRAINT deposit_status_check
                                    CHECK (status IN ('CREATING','PENDING','CONFIRMED','REFUSED','EXPIRED'));
                            END IF;
                            IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'deposit_user_idempotency_unique') THEN
                                ALTER TABLE deposit_request ADD CONSTRAINT deposit_user_idempotency_unique UNIQUE (user_id, idempotency_key);
                            END IF;
                            IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'deposit_provider_charge_unique') THEN
                                ALTER TABLE deposit_request ADD CONSTRAINT deposit_provider_charge_unique UNIQUE (provider_charge_id);
                            END IF;
                        END $$;
                        CREATE INDEX IF NOT EXISTS idx_deposit_user_created
                            ON deposit_request(user_id, created_at DESC)
                        """)))
                .await().indefinitely();
    }

    private Uni<Void> apply(SqlConnection connection, String version, String sql) {
        return connection.preparedQuery("SELECT 1 FROM app_schema_migration WHERE version = $1")
                .execute(Tuple.of(version))
                .flatMap(rows -> rows.iterator().hasNext()
                        ? Uni.createFrom().voidItem()
                        : connection.query(sql).execute()
                                .flatMap(ignored -> connection.preparedQuery(
                                                "INSERT INTO app_schema_migration(version) VALUES ($1)")
                                        .execute(Tuple.of(version)))
                                .replaceWithVoid());
    }
}
