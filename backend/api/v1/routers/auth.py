from datetime import timedelta
from typing import Annotated
from fastapi import APIRouter, Depends, HTTPException, status
from fastapi.security import OAuth2PasswordRequestForm

from core.config import settings
from core.security.jwt_manager import create_access_token
from core.security.password_manager import verify_password
from crud.user_crud import get_user_by_username
from schemas.token_schema import TokenSchema
from api.v1.deps import SessionDep

auth_router = APIRouter()


@auth_router.post("/login", response_model=TokenSchema)
async def login_for_access_token(
    session: SessionDep, 
    form_data: Annotated[OAuth2PasswordRequestForm, Depends()]
):
    user = get_user_by_username(session=session, username=form_data.username)
    if not user or not verify_password(form_data.password, user.password):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Incorrect username or password",
            headers={"WWW-Authenticate": "Bearer"},
        )
    
    access_token_expires = timedelta(minutes=settings.ACCESS_TOKEN_EXPIRE_MINUTES)
    access_token = create_access_token(
        data={"sub": str(user.id_user)}, expires_delta=access_token_expires
    )
    return {"access_token": access_token, "token_type": "bearer"}