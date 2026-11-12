
from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session

from sqlalchemy import func, text
from models import Evento, Ingresso
from schemas import EventoCreate, EventoOut
from database import get_db

evento_router = APIRouter(
    prefix="/eventos",
    tags=["Eventos"]
)


# Cadastrar um evento
@evento_router.post(
    "/",
    response_model=EventoOut,
    status_code=status.HTTP_201_CREATED
)
def criar_evento(
    dados: EventoCreate,
    db: Session = Depends(get_db)
):
    db.execute(text("BEGIN IMMEDIATE"))
    maior_id = max(db.query(func.max(Evento.id)).scalar() or 0, db.query(func.max(Ingresso.evento_id)).scalar() or 0)
    novo_evento = Evento(
        id=maior_id + 1,
        nome=dados.nome,
        data=dados.data,
        capacidade=dados.capacidade
    )

    db.add(novo_evento)
    db.commit()
    db.refresh(novo_evento)

    return novo_evento


# Listar todos os eventos
@evento_router.get("/", response_model=list[EventoOut])
def listar_eventos(db: Session = Depends(get_db)):
    return db.query(Evento).all()


# Consultar um evento pelo ID
@evento_router.get("/{evento_id}", response_model=EventoOut)
def consultar_evento(
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

    return evento


# Atualizar um evento
@evento_router.put("/{evento_id}", response_model=EventoOut)
def atualizar_evento(
    evento_id: int,
    dados: EventoCreate,
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

    evento.nome = dados.nome
    evento.data = dados.data
    evento.capacidade = dados.capacidade

    db.commit()
    db.refresh(evento)

    return evento


# Excluir um evento
@evento_router.delete("/{evento_id}")
def excluir_evento(
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

    if evento.capacidade < 0:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Capacidade inválida"
        )

    db.delete(evento)
    db.commit()

    return {"mensagem": "Evento excluído com sucesso"}
