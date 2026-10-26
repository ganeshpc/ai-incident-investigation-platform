"""HTTP application for the investigation AI service.

FastAPI is the Python web framework. It reads type hints on each function and
uses Pydantic to check the JSON it sends back. A type hint is a note, such as
``-> HealthResponse``, that says what kind of value a function returns. Python
does not require the hint. FastAPI uses it so a wrong response shape fails in
the service instead of reaching the caller unmarked.

Uvicorn is the server that receives HTTP and calls this application. Start it
with ``uv run uvicorn app.main:app --host 127.0.0.1 --port 8000``.
"""

from fastapi import FastAPI
from pydantic import BaseModel, Field


class HealthResponse(BaseModel):
    """JSON body returned by the health route.

    A Pydantic model is a class that checks fields and stores them. FastAPI
    publishes the class as the response schema. The names are the JSON keys.

    Attributes:
        service: Stable process name.
        environment_label: Says this deployment uses synthetic data.
        phase: Implementation phase this build represents.
    """

    service: str = Field(description="Stable process name.")
    environment_label: str = Field(description="Says this deployment uses synthetic data.")
    phase: str = Field(description="Implementation phase this build represents.")


def create_app() -> FastAPI:
    """Build a new FastAPI application.

    A factory is a function that returns a ready object. Tests call it so each
    test gets its own application. The name ``app`` at the bottom of this file
    is what Uvicorn imports when the process starts.

    Returns:
        The configured application. The health route does not call a model.
    """

    application = FastAPI(
        title="Investigation AI service",
        version="0.1.0",
        description=(
            "Bounded investigation workflow. This phase only reports that the "
            "process is up. It does not call a model."
        ),
    )

    @application.get("/health")
    def health() -> HealthResponse:
        """Report that the AI process is up and synthetic.

        Returns:
            A fixed health document.
        """

        return HealthResponse(
            service="investigator-ai",
            environment_label="synthetic-test",
            phase="bootstrap",
        )

    return application


# Uvicorn loads this name. Creating it at import time is the FastAPI convention.
app = create_app()
