import pytest

@pytest.fixture
def auth_token(client):
    # Helper to register and get token
    username = "taskuser"
    password = "password123"
    
    client.post( "/api/v1/users/register", json={
        "name": "Task User", "username": username, "email": "task@example.com",
        "password": password, "is_active": True
    })
    
    response = client.post("/api/v1/auth/login", data={"username": username, "password": password})
    return response.json()["access_token"]

def test_create_task(client, auth_token):

    response = client.post(
        "/api/v1/tasks/",
        headers={"Authorization": f"Bearer {auth_token}"},
        json={
            "title": "New Task",
            "description": "Description of test task",
            "status": "pending"
        },
    )
    
    assert response.status_code == 200 or response.status_code == 201
    data = response.json()
    assert data["title"] == "New Task"

def test_read_tasks(client, auth_token):
    
    client.post(
        "/api/v1/tasks/",
        headers={"Authorization": f"Bearer {auth_token}"},
        json={"title": "Task 1", "description": "Desc", "status": "pending"}
    )

    response = client.get(
        "/api/v1/tasks/",
        headers={"Authorization": f"Bearer {auth_token}"}
    )
    assert response.status_code == 200
    data = response.json()
    assert len(data) >= 1


def test_update_task(client, auth_token):

    create_response = client.post(
        "/api/v1/tasks/",
        headers={"Authorization": f"Bearer {auth_token}"},
        json={"title": "Task to Update", "description": "Desc", "status": "pending"}
    )
    task_id = create_response.json()["id_task"]

    update_response = client.put(
        f"/api/v1/tasks/{task_id}",
        headers={"Authorization": f"Bearer {auth_token}"},
        json={"title": "Updated Task", "description": "Updated Desc", "status": "completed"}
    )
    assert update_response.status_code == 200
    updated_data = update_response.json()
    assert updated_data["title"] == "Updated Task"


def test_delete_task(client, auth_token):

    create_response = client.post(
        "/api/v1/tasks/",
        headers={"Authorization": f"Bearer {auth_token}"},
        json={"title": "Task to Delete", "description": "Desc", "status": "pending"}
    )
    task_id = create_response.json()["id_task"]

    delete_response = client.delete(
        f"/api/v1/tasks/{task_id}",
        headers={"Authorization": f"Bearer {auth_token}"}
    )
    assert delete_response.status_code == 200

    get_response = client.get(
        f"/api/v1/tasks/{task_id}",
        headers={"Authorization": f"Bearer {auth_token}"}
    )
    assert get_response.status_code == 404


@pytest.fixture
def auth_token_user2(client):

    username = "taskuser2"
    password = "password456"
    
    client.post("/api/v1/users/register", json={
        "name": "Task User 2",
        "username": username,
        "email": "task2@example.com",
        "password": password,
        "is_active": True
    })
    
    response = client.post("/api/v1/auth/login", data={
        "username": username,
        "password": password
    })
    return response.json()["access_token"]

def test_user_cannot_access_other_user_task(client, auth_token, auth_token_user2):

    create_response = client.post(
        "/api/v1/tasks/",
        headers={"Authorization": f"Bearer {auth_token}"},
        json={
            "title": "Private Task for User 1",
            "description": "Only for user 1",
            "status": "pending"
        }
    )
    task_id = create_response.json()["id_task"]
    
    response = client.get(
        f"/api/v1/tasks/{task_id}",
        headers={"Authorization": f"Bearer {auth_token_user2}"}
    )
    
    assert response.status_code  == 404 #in [403, 404]


def test_user_cannot_update_other_user_task(client, auth_token, auth_token_user2):

    create_response = client.post(
        "/api/v1/tasks/",
        headers={"Authorization": f"Bearer {auth_token}"},
        json={
            "title": "Private Task for User 1",
            "description": "Original",
            "status": "pending"
        }
    )
    task_id = create_response.json()["id_task"]
    
    response = client.put(
        f"/api/v1/tasks/{task_id}",
        headers={"Authorization": f"Bearer {auth_token_user2}"},
        json={
            "title": "Hacked Task",
            "description": "Modified illegally",
            "status": "completed"
        }
    )
    
    assert response.status_code == 404 #in [403, 404]



def test_user_cannot_delete_other_user_task(client, auth_token, auth_token_user2):

    create_response = client.post(
        "/api/v1/tasks/",
        headers={"Authorization": f"Bearer {auth_token}"},
        json={
            "title": "Private Task for User 1",
            "description": "Should not be deleted by other user",
            "status": "pending"
        }
    )
    task_id = create_response.json()["id_task"]
    

    response = client.delete(
        f"/api/v1/tasks/{task_id}",
        headers={"Authorization": f"Bearer {auth_token_user2}"}
    )
    
    assert response.status_code == 404 #in [403, 404]


def test_create_task_without_title(client, auth_token):

    response = client.post(
        "/api/v1/tasks/",
        headers={"Authorization": f"Bearer {auth_token}"},
        json={
            "description": "No title",
            "status": "pending"
        }
    )
    
    assert response.status_code == 422  # Unprocessable Entity


def test_create_task_with_invalid_status(client, auth_token):

    response = client.post(
        "/api/v1/tasks/",
        headers={"Authorization": f"Bearer {auth_token}"},
        json={
            "title": "Task",
            "description": "Test",
            "status": "invalid_status"  # Status not allowed
        }
    )
    
    assert response.status_code == 422


def test_access_task_without_authentication(client):

    response = client.get("/api/v1/tasks/")
    
    assert response.status_code == 401  # Unauthorized


def test_create_task_with_invalid_token(client):

    response = client.post(
        "/api/v1/tasks/",
        headers={"Authorization": "Bearer token_invalido_123"},
        json={
            "title": "Task",
            "description": "Test",
            "status": "pending"
        }
    )
    
    assert response.status_code == 401


def test_get_nonexistent_task(client, auth_token):

    response = client.get(
        "/api/v1/tasks/99999",  # ID that doesn't exist
        headers={"Authorization": f"Bearer {auth_token}"}
    )
    
    assert response.status_code == 404