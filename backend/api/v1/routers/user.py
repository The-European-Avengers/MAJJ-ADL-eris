from fastapi import APIRouter, HTTPException,status
from crud import user_crud
from schemas.user_schema import UserCreateSchema, UserCreatedResponseSchema, UserUpdateSchema
from api.v1.deps import SessionDep


user_router = APIRouter()



@user_router.get("/{user_id}")
async def read_user(session: SessionDep, user_id: int):
    user = user_crud.get_user_by_id(session=session, user_id=user_id)
    
    if not user:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND, 
            detail=f"User with id {user_id} not found"
        )
    return user



@user_router.post("/register",response_model=UserCreatedResponseSchema)
async def create_user(session: SessionDep, user:UserCreateSchema ):
    new_user = user_crud.create_user(session=session, user_data=user)
    return new_user


@user_router.put("/update/{user_id}")
async def update_user(session: SessionDep, user_id: int, user_data: UserUpdateSchema):
    updated_user = user_crud.update_user_by_id(session=session, user_id=user_id, user_data=user_data)

    if not updated_user:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND, 
            detail=f"User with id {user_id} not found"
        )
    return updated_user