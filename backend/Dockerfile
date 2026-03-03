
FROM python:3.14


WORKDIR /code


COPY ./requirements.txt /code/requirements.txt


RUN pip install --no-cache-dir --upgrade -r /code/requirements.txt


COPY . /code

# Run migrations and start the app
CMD ["sh", "-c", "alembic upgrade head && fastapi run main.py --port 80"]