
from datetime import date
from pydantic import BaseModel, ConfigDict, Field


# Schemas de Eventos
class EventoCreate(BaseModel):
    nome: str = Field(min_length=2)
    data: date
    capacidade: int = Field(gt=0)


class EventoOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    nome: str
    data: date
    capacidade: int


# Schemas de Participantes
class ParticipanteCreate(BaseModel):
    nome: str = Field(min_length=2)
    email: str


class ParticipanteOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    nome: str
    email: str


# Schemas de Ingressos
class IngressoCreate(BaseModel):
    participante_id: int = Field(gt=0)


class IngressoOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    evento_id: int
    participante_id: int
    codigo: str
    utilizado: bool
