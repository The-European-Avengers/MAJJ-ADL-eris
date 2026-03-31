from datetime import datetime
from typing import Optional
from pydantic import BaseModel


class UserCreateSchema(BaseModel):
    name: str 
    username: str
    email: str  
    password: str  
    is_active: bool  

class UserUpdateSchema(BaseModel):
    name: str 
    username: str
    password: str 


class UserLoginSchema(BaseModel):
    email: str 
    password: str 


class UserCreatedResponseSchema(BaseModel):
    name: str 
    username: str
    email: str  
    is_active: bool
    current_streak: int
    longest_streak: int
    total_score: float
    last_prediction_date: Optional[datetime] = None  

    class Config:
        from_attributes = True


# Esquema ligero específico para el leaderboard
class LeaderboardEntry(BaseModel):
    username: str
    current_streak: int
    total_score: float

    class Config:
        from_attributes = True
