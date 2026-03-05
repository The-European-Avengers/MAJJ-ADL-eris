# eris API

A Python-based RESTful API for managing tasks with secure authentication and database integration.

## Features

- **User Management**: Registration and JWT-based authentication.
- **Task Management**: CRUD operations for tasks (Create, Read, Update, Delete).
- **Security**: Password hashing and OAuth2 with JWT tokens.
- **Database**: PostgreSQL integration using SQLAlchemy ORM.
- **Migrations**: Database schema management with Alembic.
- **Testing**: Automated unit and integration tests using `pytest` and SQLite (in-memory).
- **Containerization**: Full Docker support for easy deployment.

## Tech Stack

- **Language**: Python 3.11+
- **Framework**: FastAPI
- **ORM**: SQLAlchemy
- **Database**: PostgreSQL (Production/Dev), SQLite (Testing)
- **Migrations**: Alembic
- **Testing**: Pytest

## Setup Instructions

## 1. Unzip the file 


### Option 1: Using Docker (Recommended)

1. **Configure Environment Variables**:
   Rename the provided `.env.template` file to `.env` and fill in the values.
   ```env
   USER_DATABASE=postgres
   PASSWORD_DATABASE=password
   HOST_DATABASE=db
   PORT_DATABASE=5432
   NAME_DATABASE=eris_db
   DATABASE_URL=postgresql://postgres:password@db:5432/eris_db
   SECRET_KEY=replace_with_a_secure_secret_key
   ALGORITHM=HS256
   ACCESS_TOKEN_EXPIRE_MINUTES=30
   ```

2. **Build and run the containers**:
   ```bash
   docker-compose up -d --build
   ```
   The API will be available at `http://localhost:8085`.

### Option 2: Local Development

1. **Create and activate a virtual environment**:
   ```bash
   python -m venv .venv
   # Windows
   .venv\Scripts\activate
   # Linux/Mac
   source .venv/bin/activate
   ```

2. **Install dependencies**:
   ```bash
   pip install -r requirements.txt
   ```

3. **Configure Environment**:
   Update your `.env` file to point to a local PostgreSQL instance (change `HOST_DATABASE` to `localhost`).
   
   
4. **Run Migrations**:
   ```bash
   alembic upgrade head
   ```

5. **Start the server**:
   ```bash
   fastapi dev main.py
   ```

## API Documentation

Once the application is running, you can access the interactive API documentation provided by Swagger UI:

- **Swagger UI**: `http://localhost:8085/docs` (or `http://localhost:8000/docs` for local dev)
- **ReDoc**: `http://localhost:8085/redoc`

### Key Endpoints

- **POST** `/api/v1/auth/login`: Get JWT access token.
- **POST** `/api/v1/users/register`: Register a new user.
- **GET** `/api/v1/tasks/`: List all tasks for the authenticated user.
- **POST** `/api/v1/tasks/`: Create a new task.
- **PUT** `/api/v1/tasks/{id}`: Update a task.
- **DELETE** `/api/v1/tasks/{id}`: Delete a task.

## Running Tests

Tests are written using `pytest` and use an in-memory SQLite database to ensure isolation.

### Option 1: Local Environment

```bash
pytest
```

### Option 2: Docker Environment

If running the application via Docker, you must execute the tests inside the container:

1. **Access the container**:
   ```bash
   docker exec -it eris_app bash
   ```

2. **Run the tests**:
   ```bash
   pytest
   ```

## Design Decisions

- **FastAPI**: Chosen for its high performance, automatic validation (Pydantic), and built-in interactive documentation.
- **Project Structure**: Organized by domain (routers, models, crud, schemas) to ensure scalability and maintainability (`api/`, `core/`, `crud/`, `models/`, `schemas/`).
- **API Versioning**: URLs are prefixed with `/api/v1` to allow for future updates without breaking existing client integrations.
- **Dependency Injection**: Used extensively (e.g., database sessions, current user) to make the code modular and testable.
- **Alembic**: Used for database migrations to handle schema changes reliably over time.
- **Docker**: Included to provide a consistent environment for deployment and development, orchestrating both the application and the PostgreSQL database.


### Assumptions
- **Data Privacy**: Users can only access and manage their own tasks. Admin roles were not part of the initial scope.
- **Token Expiry**: JWT tokens are set to expire after a short duration (e.g., 30 minutes) to ensure security, requiring users to re-login or refresh.
- **Task Status**: Tasks use a status workflow (`pending`, `in_progress`, `completed`) instead of a boolean flag, defaulting to `pending` upon creation.