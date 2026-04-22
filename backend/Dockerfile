# Usamos una versión estable y ligera
FROM python:3.12-slim

WORKDIR /code

# Instalamos dependencias del sistema necesarias para algunas librerías de Python (si usas psycopg2 por ejemplo)
RUN apt-get update && apt-get install -y --no-install-recommends \
    gcc \
    libpq-dev \
    && rm -rf /var/lib/apt/lists/*

COPY ./requirements.txt /code/requirements.txt

RUN pip install --no-cache-dir --upgrade -r /code/requirements.txt

COPY . /code

# IMPORTANTE: Escuchar en 0.0.0.0 para que Render pueda redirigir el tráfico
CMD ["sh", "-c", "alembic upgrade head && fastapi run main.py --port 80 --host 0.0.0.0"]