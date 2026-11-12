
from pathlib import Path
import os
from dotenv import load_dotenv

load_dotenv(Path(__file__).resolve().parent.parent / ".env", encoding="utf-8-sig")
from sqlalchemy.engine import make_url
from sqlalchemy import (
    create_engine,
    Column,
    Integer,
    String,
    Boolean,
    Date,
    ForeignKey,
    UniqueConstraint
)
from sqlalchemy.orm import declarative_base

# DATABASE_URL fica apenas no ambiente do servidor, nunca no site ou no Git.
database_url = os.environ.get("DATABASE_URL", f"sqlite:///{Path(__file__).resolve().parent / 'banco.db'}")
if database_url.startswith("postgres://"):
    database_url = "postgresql+psycopg://" + database_url[len("postgres://"):]
elif database_url.startswith("postgresql://"):
    database_url = "postgresql+psycopg://" + database_url[len("postgresql://"):]
url = make_url(database_url)
if url.get_backend_name() == "postgresql":
    url = url.update_query_dict({"sslmode": url.query.get("sslmode", "require")})
db = create_engine(url, pool_pre_ping=True, connect_args={"check_same_thread": False} if url.get_backend_name() == "sqlite" else {})

Base = declarative_base()


# Tabela Eventos
class Evento(Base):
    __tablename__ = "eventos"

    id = Column(Integer, primary_key=True, autoincrement=True)
    nome = Column(String, nullable=False)
    data = Column(Date, nullable=False)
    capacidade = Column(Integer, nullable=False)


# Tabela Participantes
class Participante(Base):
    __tablename__ = "participantes"

    id = Column(Integer, primary_key=True, autoincrement=True)
    nome = Column(String, nullable=False)
    email = Column(String, nullable=False, unique=True)


# Tabela Ingressos
class Ingresso(Base):
    __tablename__ = "ingressos"

    id = Column(Integer, primary_key=True, autoincrement=True)
    evento_id = Column(Integer, ForeignKey("eventos.id"), nullable=False)
    participante_id = Column(
        Integer,
        ForeignKey("participantes.id"),
        nullable=False
    )
    codigo = Column(String, nullable=False, unique=True)
    utilizado = Column(Boolean, default=False, nullable=False)

    __table_args__ = (
        UniqueConstraint(
            "evento_id",
            "participante_id",
            name="uq_evento_participante"
        ),
    )
