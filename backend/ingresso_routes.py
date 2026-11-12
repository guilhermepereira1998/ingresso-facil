
from uuid import uuid4

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session
from sqlalchemy import text
from sqlalchemy.exc import IntegrityError

from models import Evento, Participante, Ingresso
from schemas import IngressoCreate, IngressoOut
from database import get_db

ingresso_router = APIRouter(
    prefix="/ingressos",
    tags=["Ingressos"]
)


# Emitir ingresso para um evento
@ingresso_router.post(
    "/evento/{evento_id}",
    response_model=IngressoOut,
    status_code=status.HTTP_201_CREATED
)
def emitir_ingresso(
    evento_id: int,
    dados: IngressoCreate,
    db: Session = Depends(get_db)
):
    if db.get_bind().dialect.name == "sqlite":
        db.execute(text("BEGIN IMMEDIATE"))
    else:
        db.execute(text("LOCK TABLE eventos IN EXCLUSIVE MODE"))
    evento = db.query(Evento).filter(
        Evento.id == evento_id
    ).first()

    if evento is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Evento não encontrado"
        )

    participante = db.query(Participante).filter(
        Participante.id == dados.participante_id
    ).first()

    if participante is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Participante não encontrado"
        )

    total_ingressos = db.query(Ingresso).filter(
        Ingresso.evento_id == evento_id
    ).count()

    if total_ingressos >= evento.capacidade:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Não há mais ingressos disponíveis para este evento"
        )

    ingresso_existente = db.query(Ingresso).filter(
        Ingresso.evento_id == evento_id,
        Ingresso.participante_id == dados.participante_id
    ).first()

    if ingresso_existente:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Este participante já possui ingresso para o evento"
        )

    novo_ingresso = Ingresso(
        evento_id=evento_id,
        participante_id=dados.participante_id,
        codigo=uuid4().hex[:12].upper(),
        utilizado=False
    )

    db.add(novo_ingresso)
    try:
        db.commit()
    except IntegrityError:
        db.rollback()
        raise HTTPException(status_code=409, detail="Não foi possível emitir o ingresso: cadastro duplicado.")
    db.refresh(novo_ingresso)

    return novo_ingresso


# Listar todos os ingressos
@ingresso_router.get("/", response_model=list[IngressoOut])
def listar_ingressos(db: Session = Depends(get_db)):
    return db.query(Ingresso).all()


# Consultar ingresso pelo ID
@ingresso_router.get("/{ingresso_id}", response_model=IngressoOut)
def consultar_ingresso(
    ingresso_id: int,
    db: Session = Depends(get_db)
):
    ingresso = db.query(Ingresso).filter(
        Ingresso.id == ingresso_id
    ).first()

    if ingresso is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Ingresso não encontrado"
        )

    return ingresso


# Listar ingressos de um evento
@ingresso_router.get(
    "/evento/{evento_id}",
    response_model=list[IngressoOut]
)
def listar_ingressos_evento(
    evento_id: int,
    db: Session = Depends(get_db)
):
    evento = db.query(Evento).filter(
        Evento.id == evento_id
    ).first()

    if evento is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Evento não encontrado"
        )

    return db.query(Ingresso).filter(
        Ingresso.evento_id == evento_id
    ).all()


# Marcar ingresso como utilizado
@ingresso_router.patch(
    "/{ingresso_id}/usar",
    response_model=IngressoOut
)
def usar_ingresso(
    ingresso_id: int,
    db: Session = Depends(get_db)
):
    ingresso = db.query(Ingresso).filter(
        Ingresso.id == ingresso_id
    ).first()

    if ingresso is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Ingresso não encontrado"
        )

    if ingresso.utilizado:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Este ingresso já foi utilizado"
        )

    ingresso.utilizado = True
    db.commit()
    db.refresh(ingresso)

    return ingresso
