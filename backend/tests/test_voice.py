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


def test_speech_connection_pool_lives_for_application_lifetime():
    app = create_app()
    with TestClient(app) as client:
        assert hasattr(app.state, "voice_http")
        speech = app.state.voice_http
        assert not speech.is_closed
        client.get("/health")
        assert app.state.voice_http is speech
    assert speech.is_closed


def test_kannada_is_accepted_before_provider_configuration(monkeypatch):
    monkeypatch.delenv("SARVAM_API_KEY", raising=False)
    with TestClient(create_app()) as client:
        response = client.post("/voice/transcribe", json={"audio_base64": "AAAA", "language_code": "kn-IN"})
        assert response.status_code == 503
