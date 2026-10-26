# ADR 0002: Python runs the AI workflow as a separate service

## Status

Accepted

## Date

2026-10-26

## Context

Hypothesis generation and report writing need LangGraph and an LLM client. Those libraries are Python libraries. Mixing them into the JVM process would couple the system of record to model-client releases.

## Decision

A FastAPI service owns the LangGraph workflow, Pydantic request and response schemas, hypothesis assessment, and report drafting. The Spring Boot backend calls it. The service is stateless in the first version: one request in, one validated response out. Stage state stays in PostgreSQL.

The model provider is configurable. The selected provider is OpenAI, model `gpt-6-luna`, reasoning effort `none`, through the official OpenAI Python SDK and the Responses API. Tests use a fake provider and do not call the network. xAI remains an alternate OpenAI-compatible provider.

## Alternatives considered

- Calling the model from Spring Boot with Spring AI. Rejected because the plan requires Python, FastAPI, and LangGraph.
- A LangGraph checkpoint database in Phase 0. Rejected until a graph must resume after a process restart. The attempt row is the durable stage record.
- Hard-coding one vendor SDK with no configuration switch. Rejected because the provider must be replaceable from configuration.

## Consequences

- The browser never receives `OPENAI_API_KEY`.
- The model does not receive built-in tools such as web search, file search, or computer use.
- A LangGraph checkpoint store is a later decision, not a Phase 0 dependency.
