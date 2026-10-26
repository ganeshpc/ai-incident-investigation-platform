# AI Incident Investigation Platform

## Project

This repository is an AI-assisted incident investigation platform. It helps an engineer investigate a failed batch job or workflow from synthetic evidence. The execution spec is [`docs/implementation-plan.md`](docs/implementation-plan.md).

Decisions recorded on 2026-10-26:

- Backend: Java 21, Spring Boot 4.1.x, Maven, Flyway, PostgreSQL. Use springdoc-openapi 3.x when it matches that Boot line.
- AI service: Python, FastAPI, LangGraph, and Pydantic. The LLM provider is configurable. Tests use a fake provider. xAI is one supported OpenAI-compatible provider (`XAI_API_KEY`, `https://api.x.ai/v1`). Another OpenAI-compatible provider must be selectable from configuration.
- Frontend: React, TypeScript, and Vite.
- Local runtime: Docker Compose.
- Evidence sources start as simulated connectors. A real connector is read-only and is enabled only after its official API and access requirements are verified.
- Layout: monorepo at this repository root (`backend/`, `ai-service/`, `frontend/`, `fixtures/`, `docs/`, `evaluation/`, `scripts/`).
- Git: scaffold and the execution plan stay on `master`. Each implementation phase gets its own branch and pull request.

Application code is not in the tree yet. Record commands here when a phase adds them.

## Setup

- Copy `.env.example` to `.env` and fill in local values. `.env` is gitignored.
- With [direnv](https://direnv.net/) installed, run `direnv allow` once. `.envrc` loads `.env`.
- When you add a setting, add it to `.env.example` with a placeholder and a one-line comment.
- Do not print secret values, and do not paste them into code, docs, commits, or logs.

## Commands

None yet. When a toolchain exists, list the exact commands here:

- Install
- Dev server
- Test (full suite and one file)
- Lint and format
- Typecheck or build

Run the relevant checks before finishing a task, and fix failures you caused.

## Conventions

- Follow `.editorconfig`.
- Keep this file short and agent-facing. Put human onboarding in `README.md`.
- Match the style of surrounding code once it exists. Do not reformat unrelated files.
- Do not add dependencies, licenses, CI, or services unless the task asks for them.
- `CLAUDE.md` only imports this file. Edit instructions here.

## Documentation

Write the code so a newcomer with only basic programming knowledge can understand it from the comments alone. Use the language's normal doc form (docstring, JSDoc, rustdoc, or the closest equivalent) and add a block comment where that form does not exist.

Document every module, class, type, function, and method, including private and internal ones. Document every variable, constant, field, and parameter whose name does not already teach the idea. Skip only obvious loop indexes and other names that add nothing.

Each comment must teach, in plain words:

- What this piece is and why it exists.
- The language concept it uses, explained as if the reader has not seen that concept before. Name the concept, then say what it does here.
- The technology it uses (library, framework, protocol, database, or service), what that technology is, and how this code uses it.
- The architectural idea it belongs to (for example a layer, a boundary, or a data flow), and how this piece fits that idea.

Write comments next to the code they describe. When you change code, update its comments in the same change. Do not put secrets, credentials, or private data in comments.

## GitHub

One new feature gets its own branch and its own pull request. Do not put two features on the same branch, and do not commit feature work straight to the default branch.

Keep sessions apart:

- Before editing, check `git status` and the current branch. If you find uncommitted work or a feature branch you did not start, leave it alone. Do not switch, stash, reset, commit, rebase, or push that work.
- Start your feature from an up-to-date default branch, on a new branch whose name names the feature. If that default branch is already checked out with other work, use a separate worktree instead of disturbing it.
- Stay inside your branch and the files your feature needs. Do not edit files another session is changing.
- Do not force-push or rewrite history on a branch another session uses.

When the feature is ready, open a pull request that explains what changed and why. If this repo has no GitHub remote yet, stop and say so. Do not invent a remote, and do not merge the pull request unless the user asks.

## Secrets

- Credentials live only in `.env`.
- Ask before reading `.env`.
- `XAI_API_KEY` is the API key for xAI (`https://api.x.ai/v1`). Keep it server-side. Never put it in a browser bundle or a committed file.

## Done means

- New and changed code has the documentation described above.
- New environment variables are documented in `.env.example`.
- Once Commands lists checks, those checks pass for the code you changed.
- A new feature is on its own branch, with a pull request open when a GitHub remote exists.
