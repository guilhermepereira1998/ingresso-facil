# Banco Supabase

Projeto: hckrionlbnksakyrcuht. A API usa DATABASE_URL do ambiente do servidor; sem ela, continua usando SQLite local. Configure a URI do Session Pooler (Connect no painel), com senha codificada para URL e sslmode=require. Instale backend/requirements.txt e inicie a API normalmente. Copie .env.example para .env na raiz e preencha a URI; a API carrega esse arquivo automaticamente. Nunca publique senha no site ou no Git.

A estrutura esta em supabase/migrations. RLS habilitado sem politicas publicas: acesso pelo backend, sem liberar cadastros ao navegador via Data API. A API existente continua sem autenticacao: use em ambiente restrito ate implementar login e autorizacao.

Git versiona codigo e migracoes, nao dados vivos. Os cadastros passam a persistir no Supabase quando DATABASE_URL for configurada. Isso nao hospeda a API Python nem cria sincronizacao bidirecional com SQLite. O frontend precisa continuar apontando para a API.

O SQLite original tem um ingresso ligado ao evento ausente de ID 10. O banco original permanece intacto. Foram importados somente os registros validos (10 eventos, 2 participantes, 1 ingresso); o ingresso orfao permanece apenas no SQLite.
