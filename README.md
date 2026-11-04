# Defiro

A modular workflow engine for municipal procedures.

## Estrutura

Cada parte da aplicação corre no seu container, definido no `docker-compose.yml`:

| Pasta | Serviço | O que é |
| --- | --- | --- |
| `frontend/` | `frontend` | App React + Vite, servida por nginx (ver `frontend/README.md`) |

O backend e a base de dados entram como novos serviços quando existirem.

## Correr com Docker

    docker compose up --build

Abre o frontend em http://localhost:8080 (outra porta: `FRONTEND_PORT=3000 docker compose up --build`).

Para desenvolver com recarregamento automático, sem instalar Node na máquina:

    docker compose --profile dev up frontend-dev

Abre em http://localhost:5173 e recarrega a cada alteração em `frontend/`.
