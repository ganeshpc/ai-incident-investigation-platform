# Python, and how this AI service is built

This guide is for a reader who is new to Python. It starts with the language mechanics, then walks the files in `ai-service/` in the order Python actually runs them. The matching guide for the backend is [`java-and-the-backend.md`](java-and-the-backend.md).

Phase 0 of this service does one job: answer `GET /health` with a fixed JSON document. It does not call a model. LangGraph and the OpenAI SDK are installed so later phases can use them. The running health process does not import them.

The code below matches the repository. When a later phase changes `ai-service/app/main.py`, update this guide in the same change.

## 1. A Python file is a list of statements

A `.py` file is a module. Python reads it from top to bottom and runs each statement once, the first time something imports that module.

There is no separate compile step you invoke. The interpreter compiles the file to bytecode and stores that in `__pycache__/` so the next import can skip the parse. `__pycache__/` is a cache. It is gitignored. Deleting it is safe.

Three statement kinds carry this service:

| Statement | What it does |
|---|---|
| `from x import y` | Runs module `x` if it is not already loaded, then binds the local name `y` to an object that lives in `x`. |
| `class HealthResponse` | Creates a class object and binds the name `HealthResponse` to it. |
| `def create_app` | Creates a function object and binds the name `create_app` to it. The body does not run yet. |
| `app = create_app()` | Calls the function. This one runs at import time. |

Indentation is syntax. The body of a function or class is the lines indented under it. A space at the start of a line is not decoration. This project uses 4 spaces, set in `.editorconfig`.

A name is a label stuck on an object. `application = FastAPI(...)` creates one FastAPI object and labels it `application` inside that function. Nothing copies the object.

## 2. How a project becomes an importable program

Python does not look at the whole disk when it sees `import app`. It searches a list of directories called `sys.path`. The first match wins.

For this service, `uv run` puts `ai-service/` on that list. That is why `import app` finds the folder `ai-service/app/`.

```text
ai-service/
├── .python-version      # tells uv to use Python 3.13
├── pyproject.toml       # project name, dependency ranges, tool settings
├── uv.lock              # exact package versions and hashes
├── .venv/               # local install of those packages; gitignored
├── app/
│   ├── __init__.py      # marks app as a package
│   └── main.py          # the HTTP application
└── tests/
    ├── test_health.py
    └── test_libraries.py
```

A package is a directory Python can import. `app/__init__.py` is that marker. Its body runs when any code first executes `import app`. In this repository the body is only a docstring, so importing `app` does no work beyond loading the package.

`app/main.py` is a module inside that package. Its import name is `app.main`. The dots are folder separators, not method calls.

This project has no `src/` directory and no `[build-system]` table. It is not built into a wheel. `uv run uvicorn app.main:app` runs the source tree directly. That is a normal shape for an application. A library that other projects install would use a `src/` layout and a build backend. This service is not that kind of project.

## 3. The three files that decide which code can run

### `.python-version`

The file contains `3.13`. `uv` reads it and selects the Python 3.13 interpreter. `requires-python = ">=3.13"` in `pyproject.toml` says the same thing to anyone installing the project. The code uses syntax and types that 3.13 accepts. An older interpreter is not a supported way to run it.

### `pyproject.toml`

This is the project description. The `[project]` table is the part installers read:

```toml
[project]
name = "investigator-ai"
version = "0.1.0"
requires-python = ">=3.13"
dependencies = [
    "fastapi[standard]>=0.162.0",
    "langgraph>=1.2.17",
    "openai>=3.37.0",
]
```

`fastapi[standard]` is one dependency with an extra. The word in brackets asks the installer for FastAPI plus the extra packages the FastAPI project groups under the name `standard`. That extra is what brings in Uvicorn, the program that listens on a port. The square brackets are not a Python list. They are dependency syntax inside the quoted string.

`>=0.162.0` is a range, not the version that will actually be installed. The range says "0.162.0 or newer." The lockfile is what makes "newer" stop moving.

`[dependency-groups] dev` lists tools used to check the code: pytest, Ruff, mypy, and httpx. They are not required to answer `/health`. `uv sync` in this repository installs them because we develop here. A production install can skip the `dev` group.

The `[tool.ruff]`, `[tool.mypy]`, and `[tool.pytest.ini_options]` tables are settings for those tools. They are not read by Python itself.

- Ruff checks style and sorts imports. `known-first-party = ["app"]` tells it that `app` is our code, so imports of `app` stay in their own block under third-party imports.
- mypy reads the type hints and reports mismatches. It does not start the server and does not call OpenAI. `strict = true` turns on the stricter set of those checks.
- pytest looks in `tests/` because `testpaths = ["tests"]`. `pythonpath = ["."]` puts `ai-service/` on `sys.path` during tests, which is the same reason `import app` works.

### `uv.lock`

`uv lock` writes this file. Every direct dependency and every package those dependencies need is pinned to one version, with hashes. `uv sync` installs that set into `.venv/`.

Editing a version in `pyproject.toml` does nothing to the installed packages until you run `uv lock` and `uv sync` again. The virtual environment is a private folder of libraries for this project. It is gitignored so each machine builds its own copy from the lockfile.

Two consequences:

- Two checkouts with the same lockfile get the same library versions.
- A library can be installed and still be unused. `langgraph` and `openai` are in the lockfile. `app/main.py` does not import them. Starting the server does not load them.

## 4. What the running process is

```text
you
  →  uv run uvicorn app.main:app --host 127.0.0.1 --port 8000
       →  uv selects Python 3.13 and the project's .venv
            →  Uvicorn imports the module app.main
                 →  that import calls create_app() and binds the result to app
                      →  Uvicorn keeps that object and serves HTTP with it
```

The string `app.main:app` is Uvicorn's notation, not Python syntax. The part before the colon is the module to import. The part after the colon is the attribute on that module. Uvicorn wants the attribute to be a FastAPI application. In this file, that attribute is the name `app` created on the last line.

`--host 127.0.0.1` binds the listening socket to loopback. Other machines cannot open the port. `--port 8000` is the port the Spring Boot backend will call in a later phase. Nothing in Python makes 8000 special.

Uvicorn is an ASGI server. ASGI is the calling convention between a Python web server and an application: the server parses the HTTP bytes, then calls the application with a structured request, then writes the application's response back as HTTP bytes. FastAPI implements that convention. You do not write the socket code.

## 5. The module, line by line

The file is `ai-service/app/main.py`.

### The docstring

```python
"""HTTP application for the investigation AI service.
...
"""
```

A triple-quoted string as the first statement of a module, function, or class is a docstring. Python stores it on the object as `__doc__`. It does not run as a command. FastAPI does not read this module docstring. The description clients see in the API document is the `description=` argument passed to `FastAPI(...)` further down.

### The imports

```python
from fastapi import FastAPI
from pydantic import BaseModel, Field
```

`import` executes the named module if `sys.modules` does not already contain it, then caches the module object there. A second import in the same process does not run the file again. It reuses the cached module.

`from fastapi import FastAPI` does not copy FastAPI's source into this file. It looks up the name `FastAPI` inside the already loaded `fastapi` module and binds that same class object to the local name `FastAPI`.

Those two lines are why this module depends on the installed packages. If `.venv` is missing or `uv sync` was never run, the import fails before any route exists, with `ModuleNotFoundError`.

### The response shape

```python
class HealthResponse(BaseModel):
    service: str = Field(description="Stable process name.")
    environment_label: str = Field(description="Says this deployment uses synthetic data.")
    phase: str = Field(description="Implementation phase this build represents.")
```

`class` creates a class. `HealthResponse` inherits from Pydantic's `BaseModel`. Inheritance means `HealthResponse` is a `BaseModel`, plus the three fields declared here.

`service: str` is a type hint. CPython does not check it when the program runs. You can see the hints later by inspecting the class, but a normal assignment does not reject a wrong type.

Pydantic is the layer that does check. Constructing a `HealthResponse` reads the hints and the `Field(...)` defaults and validates the values you passed. `Field(description=...)` does not change the stored value. The description is metadata. FastAPI copies it into the generated OpenAPI schema so `/docs` can show it.

The Python attribute name is the JSON key. This model serializes as:

```json
{
  "service": "investigator-ai",
  "environment_label": "synthetic-test",
  "phase": "bootstrap"
}
```

There is no alias turning `environment_label` into `environmentLabel`. The Java status route uses `environmentLabel`. The two processes are not sharing a class. A later client has to treat those names as different contracts until a phase defines one shared shape.

If you construct `HealthResponse(service="investigator-ai")` and omit the other two fields, Pydantic raises `ValidationError` immediately. The object is never created.

### The factory

```python
def create_app() -> FastAPI:
    application = FastAPI(
        title="Investigation AI service",
        version="0.1.0",
        description=(
            "Bounded investigation workflow. This phase only reports that the "
            "process is up. It does not call a model."
        ),
    )
    # route registration happens here, shown in the next section
    return application
```

`def` creates a function object. The body runs only when something calls `create_app()`. `-> FastAPI` is a hint that the function returns a FastAPI application. mypy uses the hint. Python does not.

`FastAPI(...)` calls the class. Calling a class constructs an instance. That instance is the application object: the route table, the settings, and the OpenAPI metadata live on it.

The parentheses around the description string are Python's way to split one string across lines. Adjacent string literals inside those parentheses are joined into one string. Nothing about FastAPI requires the parentheses.

A factory exists so each caller can get a new application object. The test does that. The server uses one object created at import, shown at the bottom of the file.

### The route

```python
    @application.get("/health")
    def health() -> HealthResponse:
        return HealthResponse(
            service="investigator-ai",
            environment_label="synthetic-test",
            phase="bootstrap",
        )
```

This `def` is nested. It runs when `create_app()` runs, not when the module is imported. Each call to `create_app()` defines a new function object named `health` and registers that object on that application.

`@application.get("/health")` is a decorator. It is shorthand for: define `health`, then immediately replace it with the result of `application.get("/health")(health)`. `application.get("/health")` returns a function whose job is to record a route. Calling that recorder with `health` stores a mapping:

```text
HTTP GET, path /health  →  this function object
```

The path is a string matched against the URL path. It is not a filesystem path. Nothing is read from a `health` file.

`health` closes over nothing from the factory except the fact that it was registered on `application`. The three strings are literals. Every successful call returns the same three values. There is no request body, no header, and no database read.

The return annotation `-> HealthResponse` is how FastAPI knows the response schema. FastAPI will also validate the returned value against that model before sending it. A returned dict with the same keys is coerced into the model. A returned dict missing `phase` becomes an internal error, which FastAPI reports as HTTP 500, not as a normal health body. This function returns a real `HealthResponse`, so that path does not run.

### The object the server keeps

```python
app = create_app()
```

This line is not inside a function. It runs when `app.main` is imported. The import's side effect is "build the application."

That is the usual FastAPI shape, and it has a cost. Any code that imports `app.main` builds an application, including a future module that only wanted a helper from this file. Tests avoid depending on this shared object. They call `create_app()` themselves so each test owns the application it calls.

There is no `if __name__ == "__main__"` block. That guard is how a file can behave differently when you run it as a program than when you import it. This file is always imported, by Uvicorn or by tests. Uvicorn is the program.

## 6. One request, from socket to JSON

`GET http://127.0.0.1:8000/health` with the server running:

1. The operating system delivers the TCP connection to Uvicorn because Uvicorn owns `127.0.0.1:8000`.
2. Uvicorn parses the HTTP request line and headers into an ASGI request.
3. FastAPI walks the route table on the `app` object and finds the `health` function registered for `GET /health`.
4. FastAPI calls `health()`. There is nothing to parse from the request.
5. `health` constructs a `HealthResponse`. Pydantic checks that all three values are strings.
6. FastAPI serializes that model to JSON. The keys are the field names.
7. Uvicorn writes `200` and `Content-Type: application/json`, then the body.

A path FastAPI does not know, such as `GET /investigate`, does not reach `health`. FastAPI itself returns 404. No function in this repository handles that case.

FastAPI also builds two documents from the same route table:

- `GET /openapi.json` is the machine-readable schema, including the `Field` descriptions.
- `GET /docs` is a browser page over that schema.

Those routes come from `FastAPI(...)`. This repository did not register them by hand.

While the server is running, nothing in this process reads `OPENAI_API_KEY`. The key is unused until a later phase imports the OpenAI SDK and makes a request. A missing key does not stop `/health`.

## 7. The package marker

`ai-service/app/__init__.py` is a docstring and nothing else. It exists so `app` is a regular package. Without it, Python can still treat `app` as a namespace package in some layouts, and tools then disagree about whether `app.main` is importable. The empty-of-code file removes that ambiguity.

Putting the FastAPI object in `__init__.py` would run `create_app()` for every `import app`, including imports that only wanted a future submodule. Keeping construction in `app.main` limits that side effect to importers of `app.main`.

## 8. The tests

Tests live in `tests/` and are not part of the running server. pytest finds a function whose name starts with `test_`.

### The health test

```python
from fastapi.testclient import TestClient

from app.main import create_app


def test_health_reports_synthetic_environment() -> None:
    client = TestClient(create_app())
    response = client.get("/health")

    assert response.status_code == 200
    body = response.json()
    assert body["service"] == "investigator-ai"
    assert body["environment_label"] == "synthetic-test"
    assert body["phase"] == "bootstrap"
```

`TestClient` is Starlette's in-memory client. Starlette is the web toolkit inside FastAPI. The client calls the ASGI application directly. No socket opens, so the test does not need port 8000 to be free and does not need Uvicorn.

`create_app()` here is a second application, not the module-level `app`. The test would also pass against the shared `app`, because `health` has no stored state. Calling the factory is the habit that stays correct when a later route stores something on the application.

`assert` raises `AssertionError` when the condition is false. pytest turns that into a failed test. `response.json()` parses the response body and returns a dict. The keys are strings, which is why the lookup is `body["environment_label"]` and not an attribute.

`-> None` means the test function returns nothing. The interesting result is the assertion, not a return value.

### The library test

```python
from importlib.metadata import version

import fastapi
import langgraph
import openai


def test_pinned_libraries_import() -> None:
    assert fastapi.__version__ == version("fastapi")
    assert version("langgraph")
    assert openai.__version__ == version("openai")
    assert langgraph.__name__ == "langgraph"
```

`importlib.metadata` is in the Python standard library. `version("fastapi")` reads the version recorded when that distribution was installed into the environment. It does not ask the network.

`import langgraph` loads the library. That is the whole point of the LangGraph lines. The server module never does this import, so this test is the check that the lockfile actually installed it. `langgraph.__name__` is the module's own name, always `"langgraph"` if that import succeeded. It is not a version.

This test does not construct an OpenAI client and does not read `OPENAI_API_KEY`. Importing `openai` only loads the SDK.

## 9. Nuances that matter in this repository

**Import order is execution order.** `create_app` is defined before `app = create_app()` because the call has to come after the `def`. Moving the call above the `def` raises `NameError`.

**The nested `health` function is not reusable by name from outside.** After `create_app()` returns, the name `health` is local to that call. Other modules reach the behavior through HTTP on the application, or by calling `create_app()` and using `TestClient`. They do not write `from app.main import health`.

**Two calls to `create_app()` do not share routes.** Each call builds a new `FastAPI` instance and registers a new `health` function on it. That is what makes the factory useful.

**Type hints are not a gate by themselves.** mypy can fail the check command while `uv run uvicorn ...` still starts, if you skip mypy. Pydantic is the gate that runs in the process, and it only gates values that pass through a model.

**JSON names are the field names.** Adding a Python alias later is a contract change. The Java backend's status JSON is camel case. Do not assume one service's JSON will match the other's because the words look similar.

**Installed is not the same as used.** `uv.lock` contains LangGraph and OpenAI because `pyproject.toml` depends on them. `app/main.py` does not import them. A stack trace from `GET /health` will not include those libraries.

**The dev tools do not serve traffic.** Ruff rewrites or rejects source. mypy reads hints. pytest runs functions named `test_*`. None of them bind port 8000.

**`fastapi[standard]` is wider than the imports in `main.py`.** The extra installs the server and several supporting libraries. You will see them in `uv.lock` without seeing them in `main.py`.

**Secrets stay out of this process for now.** `.env` is not read by `app/main.py`. A later phase that calls OpenAI must read `OPENAI_API_KEY` inside the Python process and must not send it to the browser.

**Failure before the route.** If Pydantic or FastAPI cannot be imported, the process dies at startup. If the route runs and the handler raises, FastAPI turns the uncaught exception into HTTP 500. `/health` as written does not have a branch that raises.

## 10. What a later phase adds, and where it will go

The investigation workflow is not in this file. When it arrives, the shape should stay the same kind of Python:

- A Pydantic model for the request the Spring Boot backend sends, and a Pydantic model for the report it expects back. Those models are the contract. The type hints on the route function point FastAPI at them.
- A function that builds a LangGraph graph. LangGraph is a library for a fixed set of steps that pass a state object from step to step. The graph should be constructed in its own module under `app/`, then called by a route. It should not be hidden inside `health`.
- An OpenAI client constructed from configuration (`LLM_PROVIDER`, `OPENAI_MODEL`, `OPENAI_REASONING_EFFORT`, and the API key). Tests should keep calling a fake, the way `test_health.py` never opens a socket and `test_libraries.py` never calls the API.

`create_app()` remains the place that attaches routes to a new application, so tests can keep building a fresh one.
