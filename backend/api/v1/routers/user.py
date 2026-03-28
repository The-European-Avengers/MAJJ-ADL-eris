from backend.models.user_model import User
from fastapi import APIRouter, Depends, HTTPException, status
from crud import user_crud
from schemas.user_schema import LeaderboardEntry, UserCreateSchema, UserCreatedResponseSchema, UserUpdateSchema
from api.v1.deps import get_current_user, get_db



from sqlalchemy.orm import Session
from typing import List



user_router = APIRouter()


@user_router.post("/register",response_model=UserCreatedResponseSchema)
async def create_user(user: UserCreateSchema, session: Session = Depends(get_db)):
    new_user = user_crud.create_user(session=session, user_data=user)
    return new_user


@user_router.put("/update/{user_id}")
async def update_user(user_id: int, user_data: UserUpdateSchema, session: Session = Depends(get_db)):
    updated_user = user_crud.update_user_by_id(session=session, user_id=user_id, user_data=user_data)

    if not updated_user:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND, 
            detail=f"User with id {user_id} not found"
        )
    return updated_user


@user_router.post("/prediction-complete", response_model=UserCreatedResponseSchema)
def register_prediction_activity(
    points: float = 10.0, # Los puntos que gana al ejecutar el modelo
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user)
):
    """
    Este endpoint debe ser llamado desde la app móvil justo después 
    de ejecutar el modelo de Machine Learning con éxito.
    """
    updated_user = user_crud.update_user_streak(session=db, user_id=current_user.id_user, points_earned=points)
    if not updated_user:
        raise HTTPException(status_code=404, detail="Usuario no encontrado")
    
    return updated_user

@user_router.get("/leaderboard", response_model=List[LeaderboardEntry])
def fetch_leaderboard(
    limit: int = 10,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user) 
):
    """
    Devuelve los N mejores usuarios ordenados por puntuación.
    """
    users = user_crud.get_leaderboard(session=db, limit=limit)
    return users


@user_router.get("/{user_id}")
async def read_user(user_id: int, session: Session = Depends(get_db)):
    user = user_crud.get_user_by_id(session=session, user_id=user_id)

    if not user:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=f"User with id {user_id} not found"
        )
    return user