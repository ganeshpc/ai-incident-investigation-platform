# Contributing

## Branches

One implementation phase gets one branch and one pull request. Start the branch from an up-to-date `master`. Do not put two phases on the same branch.

## Before you open a pull request

Run the commands in [`docs/development-standards.md`](docs/development-standards.md) for every area you changed. Fix failures you caused.

New code follows the comment rule in [`AGENTS.md`](AGENTS.md).

## Secrets and data

Do not commit `.env`, API keys, access tokens, or production logs. Fixtures are synthetic. Do not add employer code, customer data, or real release records.

When you add a setting, add a placeholder and a one-line comment to `.env.example`.
