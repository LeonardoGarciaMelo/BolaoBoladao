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
