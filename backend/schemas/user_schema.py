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
