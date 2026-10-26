# Security

This repository is a local synthetic investigation tool. It is not a production deployment.

## Reporting a problem

Open a private security advisory on the GitHub repository if one is enabled. Do not include live credentials in the report.

## Rules for this codebase

- Secrets stay in `.env` or a secret manager. `.env` is gitignored.
- `OPENAI_API_KEY` and `XAI_API_KEY` are read only by the Python AI service. They are never written into the frontend bundle, logs, or fixtures.
- Phase 0 binds the API, the AI service, and the UI to `127.0.0.1`. Do not expose them on a public network without authentication.
- The local demo has no login. That is acceptable only on localhost.
- Future source connectors are read-only. They do not start jobs, change case state, or run remediation.
- Logs and model prompts must not contain secrets or full evidence bodies.
- A request must not choose an arbitrary URL, shell command, or file path for a connector.

## Data

The committed fixtures are synthetic. Evidence sent to an external model provider, once that call exists, must be the bounded synthetic bundle for that incident. Do not send credentials.
