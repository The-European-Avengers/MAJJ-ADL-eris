from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from core.db.db import engine
from models import Base  # Import Base and all models
from api.v1.main import api_router
from core.config import settings


origins = [
    "http://localhost",
    "http://localhost:8080",
]

from core.database import engine # Ajusta la ruta a donde tengas tu 'engine'
from models.base import Base     # Importa la Base
import models                    # Importa el __init__ para que registre User y Task


Base.metadata.create_all(bind=engine)

app = FastAPI()

def configure_app():
    app = FastAPI()
    app.title = "Qpurpose API"
    app.version = "1.0.0"
    return app

app = configure_app()

app.add_middleware(
    CORSMiddleware,
    allow_origins=origins,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(api_router, prefix=settings.API_VERSION)




