from pydantic import BaseModel, EmailStr
from enum import Enum

class TaskStatus(str, Enum):
    PENDING = "pending"
    IN_PROGRESS = "in_progress"
    COMPLETED = "completed"


class TaskCreateSchema(BaseModel):
    title: str
    description: str
    status: TaskStatus

class TaskUpdateSchema(BaseModel):
    title: str
    description: str
    status: TaskStatus


