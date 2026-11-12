
from pathlib import Path
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

# Conexão com o banco de dados SQLite
db = create_engine(f"sqlite:///{Path(__file__).resolve().parent / 'banco.db'}", connect_args={"check_same_thread": False})

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
