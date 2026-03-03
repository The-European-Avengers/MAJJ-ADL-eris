

from fastapi.testclient import TestClient

from main import app
client = TestClient(app)


def test_create_user(client):

    response = client.post(
        "/api/v1/users/register",
        json={
            "name": "Test User",
            "username": "testuser",
            "email": "test@example.com",
            "password": "securepassword123",
            "is_active": True
        },
    )
    assert response.status_code == 200
    data = response.json()
    assert data["email"] == "test@example.com"


def test_login_user(client):
   
    client.post(
        "/api/v1/users/register",
        json={
            "name": "Login User",
            "username": "loginuser",
            "email": "login@example.com",
            "password": "securepassword123",
            "is_active": True
        },
    )
    
    response = client.post(
        "/api/v1/auth/login",
        data={
            "username": "loginuser",
            "password": "securepassword123"
        },
    )
    assert response.status_code == 200
    data = response.json()
    assert "access_token" in data
    assert data["token_type"] == "bearer"


def test_login_with_wrong_password(client):
   
    client.post(
        "/api/v1/users/register",
        json={
            "name": "Test User",
            "username": "testuser",
            "email": "test@example.com",
            "password": "correctpassword",
            "is_active": True
        }
    )
    
   
    response = client.post(
        "/api/v1/auth/login",
        data={
            "username": "testuser",
            "password": "wrongpassword"
        }
    )
    
    assert response.status_code == 401


def test_login_nonexistent_user(client):
  
    response = client.post(
        "/api/v1/auth/login",
        data={
            "username": "noexiste",
            "password": "password123"
        }
    )
    
    assert response.status_code == 401