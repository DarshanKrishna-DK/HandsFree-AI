from fastapi.testclient import TestClient
from backend.app import create_app


def test_missing_sarvam_key_preserves_text_fallback(monkeypatch):
    monkeypatch.delenv("SARVAM_API_KEY", raising=False)
    with TestClient(create_app()) as client:
        response = client.post("/voice/synthesize", json={"text": "Hello", "language_code": "en-IN"})
        assert response.status_code == 503
        assert "text" in response.json()["detail"].lower()


def test_voice_rejects_unsupported_language():
    with TestClient(create_app()) as client:
        response = client.post("/voice/synthesize", json={"text": "Hello", "language_code": "fr-FR"})
        assert response.status_code == 422
