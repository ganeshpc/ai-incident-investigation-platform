# Phase 4: real read-only adapters

This guide explains the switch from fixture files to a documented read API. It assumes [`phase-3-synthetic-connectors.md`](phase-3-synthetic-connectors.md). The default mode is still simulated, so a local run with no credentials behaves as it did in Phase 3.

## 1. Two modes, and no quiet fallback

Each source has a mode in `application.yml`:

```yaml
investigator:
  connectors:
    autosys:
      mode: simulated
    openshift:
      mode: simulated
    github:
      mode: simulated
    release:
      mode: simulated
```

`simulated` reads fixture files. `real` calls only that source's documented read API. `ModeSelectingConnector` is the class that chooses. If real mode is selected and the base URL or credential is missing, the result is `UNCONFIGURED` and the sentence says fixture data was not used. If the API returns an error, the result is `UNAVAILABLE` or `FAILED` and the fixture connector is not called.

A missing workload section, such as GitHub on the settlement workload, is still `UNCONFIGURED` in either mode. That decision happens before any read.

## 2. AutoSys 12.1

The documented AEWS reads used here are GET requests:

- `/AEWS/job/{name}` for the job name, type, machine, and status
- `/AEWS/job-run-info/{name}` for runs, including `runNum`, `runStartTime`, `runEndTime`, `exitCode`, and `status`
- `/AEWS/job-dependencies/{name}` for predecessor and successor names

The 12.1 web-services page describes the security of that call as HTTP Basic over HTTPS. The client sends `Authorization: Basic` and `Accept: application/json`. It does not send cancel, hold, or force-start.

Runs are kept when `runStartTime` or `runEndTime` falls in the incident window. The examples write the offset as `+0000`, so the parser inserts the colon Java requires. A numeric status is stored as the API sent it. `strStatus` is stored when the payload includes it. This client does not translate a bare number into a word the payload did not contain.

Job output is not read. No verified read-only spool operation is used, so a successful AEWS read is stored as `PARTIAL` with that gap named. The runs and dependencies that were returned are still stored. Provenance on those rows is `REAL`.

Settings, all blank by default:

- `AUTOSYS_BASE_URL`
- `AUTOSYS_USERNAME`
- `AUTOSYS_PASSWORD`

SAML2 is described for a later AutoSys release. It is not implemented here.

## 3. OpenShift and Kubernetes pod logs

Real mode lists pods and reads container logs:

- `GET /api/v1/namespaces/{namespace}/pods?labelSelector=key=value`
- `GET /api/v1/namespaces/{namespace}/pods/{name}/log?timestamps=true&sinceTime=...&limitBytes=32768`

The token is sent as `Authorization: Bearer`. The namespace and labels come from the workload file. At most five pods are read. An empty current log is followed by one call with `previous=true`, which is the documented way to ask for the previous container. If no pod matches, the result is `PARTIAL` and says the log API does not return logs for pods that no longer exist. Fixture logs are not used.

Settings:

- `OPENSHIFT_API_SERVER`
- `OPENSHIFT_TOKEN`

The JVM trust store has to trust the API server certificate. This client does not disable certificate checks.

## 4. GitHub

Real mode calls the commit REST API with `Accept: application/vnd.github+json`, a bearer token, and `X-GitHub-Api-Version: 2026-03-10`.

`GET /repos/{owner}/{repo}/commits?since=...&until=...&per_page=100` lists commits in the window. The owner and repository come from the workload. The local fixture path is ignored in real mode.

When two commits are returned, the client compares the earliest and the latest with `GET /repos/{owner}/{repo}/compare/{base}...{head}`. Changed files become `CODE_DIFF` rows. A file with no `patch`, which the docs say happens for binary files, is stored as a file name and status. The diagnostic says the compare does not show which commit was deployed.

Settings:

- `GITHUB_TOKEN`
- `GITHUB_API_BASE_URL`, default `https://api.github.com`

## 5. Release management

No release-management product is identified. Real mode does not call an API and does not read `fixtures/releases`. The result says the product is not identified.

The fields a later verified connector would need are the application and environment, the release identifier, the deployment status, the deployment time, and the artifact or commit revision when the product provides it. A missing revision stays missing.

## 6. What you can see

With the defaults, `docker compose up -d` and `cd backend && ./mvnw spring-boot:run` still collect scenario 1 from fixtures.

To require a real AutoSys call without credentials, set `investigator.connectors.autosys.mode` to `real` and leave `AUTOSYS_BASE_URL` empty. The scheduler result is `UNCONFIGURED`, `ev-s1-run-failed` is absent, and the simulated OpenShift log is still stored.

`cd backend && ./mvnw -B verify` covers the simulated path, the missing-settings path, and local HTTP servers that return the documented JSON shapes. It does not call AutoSys, OpenShift, or GitHub.
