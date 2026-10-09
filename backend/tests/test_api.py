from fastapi.testclient import TestClient
from backend.app import create_app


def test_health_is_live_without_a_model_connection():
    with TestClient(create_app(ollama_url="http://127.0.0.1:9")) as client:
        assert client.get("/health").json()["status"] == "ok"


def test_model_disconnection_is_reported():
    with TestClient(create_app(ollama_url="http://127.0.0.1:9")) as client:
        response = client.get("/model/status")
        assert response.status_code == 503
        assert response.json()["detail"] == "Ollama is unavailable. Start Ollama and retry."


def test_invalid_request_never_reaches_the_model():
    with TestClient(create_app(ollama_url="http://127.0.0.1:9")) as client:
        assert client.post("/agent/next-action", json={"goal": ""}).status_code == 422


def test_remote_ollama_destination_is_rejected():
    import pytest
    with pytest.raises(ValueError):
        create_app(ollama_url="https://example.com")
