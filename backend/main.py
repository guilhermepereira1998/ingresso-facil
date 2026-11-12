
from pathlib import Path
from fastapi import FastAPI
from fastapi.staticfiles import StaticFiles

from models import Base, db
from evento_routes import evento_router
from participante_routes import participante_router
from ingresso_routes import ingresso_router

# Cria as tabelas no banco de dados
Base.metadata.create_all(bind=db)

app = FastAPI(
    title="Sistema de Venda de Ingressos",
    description="API para cadastro de eventos, participantes e ingressos",
    version="1.0.0"
)

# Registra as rotas
app.include_router(evento_router, prefix="/api")
app.include_router(participante_router, prefix="/api")
app.include_router(ingresso_router, prefix="/api")


# A aplicação web e a API compartilham a mesma origem.
app.mount("/", StaticFiles(directory=Path(__file__).resolve().parent.parent / "site", html=True), name="site")
