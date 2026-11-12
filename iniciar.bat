@echo off
cd /d "%~dp0backend"
where py >nul 2>nul
if errorlevel 1 (echo Python nao encontrado. Instale o Python 3.10 ou superior. & pause & exit /b 1)
if not exist .venv\Scripts\python.exe py -3 -m venv .venv
.venv\Scripts\python.exe -m pip install -r requirements.txt
if errorlevel 1 (echo Erro ao instalar dependencias. & pause & exit /b 1)
echo Acesse http://127.0.0.1:8000
.venv\Scripts\python.exe -m uvicorn main:app --host 127.0.0.1 --port 8000
pause
