
from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session

from sqlalchemy import func, text
from models import Participante, Ingresso
from schemas import ParticipanteCreate, ParticipanteOut
from database import get_db

participante_router = APIRouter(
    prefix="/participantes",
    tags=["Participantes"]
)


# Cadastrar participante
@participante_router.post(
    "/",
    response_model=ParticipanteOut,
    status_code=status.HTTP_201_CREATED
)
def criar_participante(
    dados: ParticipanteCreate,
    db: Session = Depends(get_db)
):
    if db.get_bind().dialect.name == "sqlite":
        db.execute(text("BEGIN IMMEDIATE"))
    else:
        db.execute(text("LOCK TABLE participantes IN EXCLUSIVE MODE"))
    participante_existente = db.query(Participante).filter(
        Participante.email == dados.email
    ).first()

    if participante_existente:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Já existe um participante com este e-mail"
        )

    maior_id = max(db.query(func.max(Participante.id)).scalar() or 0, db.query(func.max(Ingresso.participante_id)).scalar() or 0)
    novo_participante = Participante(
        id=maior_id + 1,
        nome=dados.nome,
        email=dados.email
    )

    db.add(novo_participante)
    db.commit()
    db.refresh(novo_participante)

    return novo_participante


# Listar participantes
@participante_router.get(
    "/",
    response_model=list[ParticipanteOut]
)
def listar_participantes(db: Session = Depends(get_db)):
    return db.query(Participante).all()


# Consultar participante pelo ID
@participante_router.get(
    "/{participante_id}",
    response_model=ParticipanteOut
)
def consultar_participante(
    participante_id: int,
    db: Session = Depends(get_db)
):
    participante = db.query(Participante).filter(
        Participante.id == participante_id
    ).first()

    if participante is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Participante não encontrado"
        )

    return participante


# Atualizar participante
@participante_router.put(
    "/{participante_id}",
    response_model=ParticipanteOut
)
def atualizar_participante(
    participante_id: int,
    dados: ParticipanteCreate,
    db: Session = Depends(get_db)
):
    participante = db.query(Participante).filter(
        Participante.id == participante_id
    ).first()

    if participante is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Participante não encontrado"
        )

    outro_participante = db.query(Participante).filter(
        Participante.email == dados.email,
        Participante.id != participante_id
    ).first()

    if outro_participante:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Este e-mail já está cadastrado"
        )

    participante.nome = dados.nome
    participante.email = dados.email

    db.commit()
    db.refresh(participante)

    return participante


# Excluir participante
@participante_router.delete("/{participante_id}")
def excluir_participante(
    participante_id: int,
    db: Session = Depends(get_db)
):
    participante = db.query(Participante).filter(
        Participante.id == participante_id
    ).first()

    if participante is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Participante não encontrado"
        )

    db.delete(participante)
    db.commit()

    return {"mensagem": "Participante excluído com sucesso"}
