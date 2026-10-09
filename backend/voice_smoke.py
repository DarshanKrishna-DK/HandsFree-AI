"""Opt-in real provider round trip. Run with python -m backend.voice_smoke.

Uses generated test speech, not the phone microphone. Requires the local backend
running with a Sarvam key; makes two billable provider calls per language.
"""
import base64
import io
import time
import wave

import httpx


def main():
    phrases = {
        "en-IN": "Open Zomato and find vegetarian food.",
        "kn-IN": "ಜೊಮಾಟೊ ತೆರೆಯಿರಿ ಮತ್ತು ಸಸ್ಯಾಹಾರಿ ಆಹಾರವನ್ನು ಹುಡುಕಿ.",
    }
    with httpx.Client(base_url="http://127.0.0.1:8000", timeout=100, trust_env=False) as client:
        print("Voice configured:", client.get("/health").json()["voice_configured"])
        for language, text in phrases.items():
            started = time.perf_counter()
            response = client.post("/voice/synthesize", json={"text": text, "language_code": language})
            print(language, "synthesis HTTP", response.status_code)
            response.raise_for_status()
            encoded = response.json()["audio_base64"]
            with wave.open(io.BytesIO(base64.b64decode(encoded)), "rb") as wav:
                print("Audio seconds:", round(wav.getnframes() / wav.getframerate(), 2))
            result = client.post("/voice/transcribe", json={"audio_base64": encoded,
                "audio_format": "wav", "language_code": language})
            print("Transcription HTTP", result.status_code)
            result.raise_for_status()
            print("Transcript:", result.json()["transcript"])
            print("Round-trip seconds:", round(time.perf_counter() - started, 2))


if __name__ == "__main__":
    main()
