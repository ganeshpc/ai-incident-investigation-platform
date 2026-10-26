"""Confirms the locked investigation libraries import.

An import statement loads a library into the process. ``importlib.metadata``
is the standard-library tool that reads the version recorded when a library
was installed. This test fails when the lockfile and the installed
environment disagree. It does not call OpenAI.
"""

from importlib.metadata import version

import fastapi
import langgraph
import openai


def test_pinned_libraries_import() -> None:
    """FastAPI, LangGraph, and the OpenAI SDK are installed."""

    assert fastapi.__version__ == version("fastapi")
    assert version("langgraph")
    assert openai.__version__ == version("openai")
    assert langgraph.__name__ == "langgraph"
