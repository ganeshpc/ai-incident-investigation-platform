"""Checks the AI service health route without calling a model.

``TestClient`` is Starlette's in-memory HTTP client. Starlette is the web
toolkit FastAPI runs on. The client calls the application directly, so the
test does not open a port and does not need a network.
"""

from fastapi.testclient import TestClient

from app.main import create_app


def test_health_reports_synthetic_environment() -> None:
    """The health route identifies this process as a synthetic bootstrap."""

    client = TestClient(create_app())
    response = client.get("/health")

    assert response.status_code == 200
    body = response.json()
    assert body["service"] == "investigator-ai"
    assert body["environment_label"] == "synthetic-test"
    assert body["phase"] == "bootstrap"
