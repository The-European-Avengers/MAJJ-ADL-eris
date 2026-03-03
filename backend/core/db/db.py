from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker
from core.config import settings

engine = create_engine(settings.DATABASE_URL, echo=True, pool_size=20, max_overflow=10)
Session = sessionmaker(bind=engine)