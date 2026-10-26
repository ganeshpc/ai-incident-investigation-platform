# Development standards

Phase 0 conventions. Later phases follow these unless an ADR changes them.

## Commands

Run the commands for the code you change. The working directory is the repository root unless a command says otherwise.

Backend:

```bash
cd backend && ./mvnw -B verify
```

That compiles, runs Spotless in check mode, and runs JUnit.

AI service:

```bash
cd ai-service
uv sync
uv run ruff check .
uv run ruff format --check .
uv run mypy app tests
uv run pytest
```

Frontend:

```bash
cd frontend
npm install
npm run lint
npm run format:check
npm test
npm run build
```

Local processes, each in its own terminal, bound to localhost:

```bash
cd backend && ./mvnw spring-boot:run
cd ai-service && uv run uvicorn app.main:app --host 127.0.0.1 --port 8000
cd frontend && npm run dev
```

## Formatting and static analysis

| Area | Format | Check |
|---|---|---|
| Java | Spotless with google-java-format | `./mvnw -B verify` fails when formatting differs |
| Python | Ruff format | `ruff check` and mypy |
| TypeScript | Prettier | oxlint and `tsc -b` |

Apply Java formatting with `cd backend && ./mvnw -B spotless:apply`. Apply Python formatting with `cd ai-service && uv run ruff format .`. Apply frontend formatting with `cd frontend && npm run format`.

## Configuration

- Settings that differ by machine live in `.env`. `.env.example` lists every setting with a placeholder.
- Spring reads `backend/src/main/resources/application.yml`.
- The AI service reads environment variables. `LLM_PROVIDER=openai` selects OpenAI. Tests do not read a real key.
- Do not put a URL, shell command, or filesystem path from a request into a connector configuration.

## Logging

The backend writes structured JSON logs to the console (`logging.structured.format.console=logstash`). Log the operation, incident id, and result. Do not log secrets, tokens, raw evidence bodies, or full prompts.

The AI service uses Uvicorn's default console log in Phase 0. The same redaction rule applies when later phases add their own logs.

## Secrets

- Credentials live only in `.env` or a secret manager.
- `OPENAI_API_KEY` is the selected provider key. `XAI_API_KEY` is the alternate provider key.
- Keys stay in the Python AI service. The browser bundle must not contain them.
- Ask before reading `.env`.
- A local Postgres password in `.env.example` is a placeholder for a database that runs on this machine. It is not a production credential.

## Comments

`AGENTS.md` states the comment rule. Follow it for new code.

## Git

Each implementation phase has its own branch and pull request. Do not put two phases on one branch.
