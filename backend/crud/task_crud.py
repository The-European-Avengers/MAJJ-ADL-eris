from models.task_model import Task
from schemas.task_schema import TaskCreateSchema, TaskUpdateSchema
from sqlalchemy import select
from sqlalchemy.orm import Session


def create_task(*, current_user, session:Session, task_data: TaskCreateSchema):
    
    new_task = Task(
        title=task_data.title,
        description=task_data.description,
        status=task_data.status,
        user_id=current_user.id_user
    )

    session.add(new_task)
    session.commit()
    session.refresh(new_task)
    return new_task

def get_task_by_id(*,current_user,session:Session,task_id: int):

    statement = select(Task).where(Task.id_task == task_id)
    result = session.execute(statement)
    task = result.scalar_one_or_none() 
    if task and task.user_id != current_user.id_user:
        return None

    return task


def update_task_by_id(*,current_user,session:Session,task_id: int, task_data: TaskUpdateSchema):
    task = get_task_by_id(current_user=current_user,session=session,task_id=task_id)

    if not task:
        return None

    task.title = task_data.title
    task.description = task_data.description
    task.status = task_data.status


    session.commit()
    session.refresh(task)
    return task


def delete_task_by_id(*,current_user,session:Session,task_id: int):
    task = get_task_by_id(current_user=current_user,session=session,task_id=task_id)

    if not task:
        return None

    session.delete(task)
    session.commit()
    return task

def get_tasks_by_user(*, session: Session, user_id: int):
    statement = select(Task).where(Task.user_id == user_id)
    result = session.execute(statement)
    return result.scalars().all()