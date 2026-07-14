from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    app_name: str = "bets-service"
    database_url: str = "postgresql+psycopg://bolao:bolao@localhost:5432/apostas_db"
    kafka_bootstrap_servers: str = "localhost:29092"
    kafka_bet_topic: str = "bet-events"
    kafka_match_topic: str = "match-events"
    kafka_wallet_topic: str = "wallet-events"
    jwt_public_key_location: str = "/run/secrets/jwt-public.pem"
    jwt_issuer: str = "bolao-user-service"
    jwt_audience: str = "bolao-api"

    model_config = SettingsConfigDict(
        env_file=".env",
        env_prefix="",
        case_sensitive=False,
        extra="ignore",
    )


settings = Settings()
