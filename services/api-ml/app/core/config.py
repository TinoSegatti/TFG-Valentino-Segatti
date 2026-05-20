from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    app_env: str = "development"
    port: int = 8081
    jwt_ml_secret: str = "dev_ml_jwt_secret_minimo_64_caracteres_compartido_domain_ml"
    allowed_issuer: str = "api-domain"
    allowed_audience: str = "api-ml"
    models_store_path: str = "./models_store"


settings = Settings()
