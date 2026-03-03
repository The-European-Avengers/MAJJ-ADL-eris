from pydantic_settings import BaseSettings, SettingsConfigDict
import secrets


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8")

    # DATABASE SETTINGS
    USER_DATABASE: str
    PASSWORD_DATABASE: str
    HOST_DATABASE: str
    PORT_DATABASE: str
    NAME_DATABASE: str
    DATABASE_URL: str
    ALGORITHM: str
    SECRET_KEY: str = secrets.token_urlsafe(32)

    # APP
    API_VERSION: str = "/api/v1"
    ACCESS_TOKEN_EXPIRE_MINUTES: int = 30



settings = Settings()