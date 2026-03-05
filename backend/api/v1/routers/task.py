from fastapi import APIRouter, Depends, HTTPException,status
from crud import task_crud, user_crud

from schemas.task_schema import TaskCreateSchema, TaskUpdateSchema

from api.v1.deps import CurrentUserDep, SessionDep

task_router = APIRouter()


@task_router.get("/")
async def read_tasks(
    current_user: CurrentUserDep, 
    session: SessionDep
):
    
    return task_crud.get_tasks_by_user(session=session, user_id=current_user.id_user)


@task_router.get("/{task_id}")
async def read_task(current_user: CurrentUserDep, session: SessionDep, task_id: int):
    task = task_crud.get_task_by_id(current_user=current_user,session=session, task_id=task_id)
    
    if not task:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND, 
            detail=f"Task with id {task_id} not found"
        )
    return task


@task_router.post("/")
async def create_task(current_user: CurrentUserDep, session: SessionDep, task: TaskCreateSchema):
   
    new_task = task_crud.create_task(current_user=current_user, session=session, task_data=task)
    return new_task


@task_router.put("/{task_id}")
async def update_task(current_user: CurrentUserDep, session: SessionDep, task_id: int, task: TaskUpdateSchema):
    existing_task = task_crud.get_task_by_id(current_user=current_user,session=session, task_id=task_id)

    if not existing_task:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=f"Task with id {task_id} not found"
        )

    updated_task = task_crud.update_task_by_id(current_user=current_user,session=session, task_id=task_id, task_data=task)

    return updated_task


@task_router.delete("/{task_id}")
async def delete_task(current_user: CurrentUserDep, session: SessionDep, task_id: int):
    existing_task = task_crud.get_task_by_id(current_user=current_user,session=session, task_id=task_id)

    if not existing_task:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=f"Task with id {task_id} not found"
        )

    deleted_task = task_crud.delete_task_by_id(current_user=current_user,session=session, task_id=task_id)

    return deleted_task