
from sqlalchemy.orm import sessionmaker
from models import db

# Cria as sessões de conexão com o banco
SessionLocal = sessionmaker(
    autocommit=False,
    autoflush=False,
    bind=db
)

# Fornece uma sessão para cada requisição
def get_db():
    session = SessionLocal()
    try:
        yield session
    finally:
        session.close()
