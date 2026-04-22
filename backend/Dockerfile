FROM python:3.12-slim

WORKDIR /code

# Instalamos las librerías necesarias para compilar psycopg2
RUN apt-get update && apt-get install -y --no-install-recommends \
    gcc \
    python3-dev \
    libpq-dev \
    && rm -rf /var/lib/apt/lists/*

COPY ./requirements.txt /code/requirements.txt

# Ahora el pip install no fallará
RUN pip install --no-cache-dir --upgrade -r /code/requirements.txt

COPY . /code

CMD ["sh", "-c", "alembic upgrade head && fastapi run main.py --port 80 --host 0.0.0.0"]