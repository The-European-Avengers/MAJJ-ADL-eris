from fastapi import APIRouter
from api.v1.routers import user, auth

api_router = APIRouter()

api_router.include_router(user.user_router, prefix="/users", tags=["users"])
api_router.include_router(auth.auth_router, prefix="/auth", tags=["auth"])