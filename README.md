# AI Incident Investigation Platform

Repository for an AI incident investigation platform, set up so coding agents and people share one set of instructions.

Application code is not in this tree yet.

## Local setup

Copy the env template and fill in values on your machine:

```bash
cp .env.example .env
```

`.env` stays untracked. `.env.example` is the list of settings the project expects.

If you use [direnv](https://direnv.net/), allow the repo once so `.env` is loaded into your shell:

```bash
direnv allow
```

## Agents

Coding agents should follow [`AGENTS.md`](AGENTS.md). That file is the shared instruction source. `CLAUDE.md` imports it for Claude Code.
