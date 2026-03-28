from models.user_model import User
from schemas.user_schema import UserCreateSchema, UserUpdateSchema
from sqlalchemy import select


from sqlalchemy.orm import Session

from core.security.password_manager import hash_password

from datetime import datetime, timezone

def get_user_by_id(*,session:Session,user_id: int):

    #user = session.query(user_model.User).filter(user_model.User.id_user == user_id).first()
    statement = select(User).where(User.id_user == user_id)
    result = session.execute(statement)
    user = result.scalar_one_or_none() # Returns User or None

    return user



def create_user(*,session:Session, user_data: UserCreateSchema):
    
    new_user = User(
        name=user_data.name,
        username=user_data.username,
        email=user_data.email,
        password=hash_password(user_data.password),
        is_active=user_data.is_active
    )

    session.add(new_user)
    session.commit()
    session.refresh(new_user)
    return new_user

    
def update_user_by_id(*,session:Session,user_id: int, user_data: UserUpdateSchema):
    user = get_user_by_id(session=session,user_id=user_id)

    if not user:
        return None

    user.name = user_data.name
    user.username = user_data.username
    user.password = hash_password(user_data.password)

    session.commit()
    session.refresh(user)
    return user



def get_user_by_email(*, session: Session, email: str):
    statement = select(User).where(User.email == email)
    result = session.execute(statement)
    return result.scalar_one_or_none()


def get_user_by_username(*, session: Session, username: str):
    statement = select(User).where(User.username == username)
    result = session.execute(statement)
    return result.scalar_one_or_none()



def update_user_streak(*, session: Session, user_id: int, points_earned: float):
    # Corrección: Usamos select() y User.id_user
    statement = select(User).where(User.id_user == user_id)
    user = session.execute(statement).scalar_one_or_none()
    
    if not user:
        return None

    now = datetime.now(timezone.utc)
    
    if user.last_prediction_date is None:
        user.current_streak = 1
        user.longest_streak = 1
    else:
        last_date = user.last_prediction_date.date()
        today = now.date()
        delta_days = (today - last_date).days

        if delta_days == 1:
            user.current_streak += 1
            if user.current_streak > user.longest_streak:
                user.longest_streak = user.current_streak
        elif delta_days > 1:
            user.current_streak = 1

    user.last_prediction_date = now
    user.total_score += points_earned

    session.commit()
    session.refresh(user)
    return user

def get_leaderboard(*, session: Session, limit: int = 10):
    # Corrección: Usamos select() de SQLAlchemy 2.0
    statement = select(User).order_by(User.total_score.desc(), User.current_streak.desc()).limit(limit)
    return session.execute(statement).scalars().all()