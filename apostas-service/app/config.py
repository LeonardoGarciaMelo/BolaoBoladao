from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    app_name: str = "bets-service"
    database_url: str = "postgresql+psycopg://bolao:bolao@localhost:5432/apostas_db"
    kafka_bootstrap_servers: str = "localhost:29092"
    kafka_bet_topic: str = "bet-events"

    model_config = SettingsConfigDict(
        env_file=".env",
        env_prefix="",
        case_sensitive=False,
        extra="ignore",
    )


settings = Settings()
