"""Optional Sarvam adapter. Credentials and raw audio never enter logs."""
import base64
import binascii
import os
from typing import Literal

import httpx
from fastapi import APIRouter, HTTPException, Request
from pydantic import Field

from .contracts import StrictModel

router = APIRouter(prefix="/voice")
Language = Literal["en-IN", "kn-IN"]


class SynthesisRequest(StrictModel):
    text: str = Field(min_length=1, max_length=1000)
    language_code: Language = "en-IN"


class TranscriptionRequest(StrictModel):
    audio_base64: str = Field(min_length=1, max_length=2_700_000)
    language_code: Language = "en-IN"
    audio_format: Literal["m4a", "wav"] = "m4a"


def key_headers():
    key = os.getenv("SARVAM_API_KEY", "").strip()
    if not key:
        raise HTTPException(503, "Voice is unavailable. You can continue with text.")
    return {"api-subscription-key": key}


@router.post("/transcribe")
async def transcribe(request: TranscriptionRequest, connection: Request):
    headers = key_headers()
    try:
        audio = base64.b64decode(request.audio_base64, validate=True)
    except (ValueError, binascii.Error):
        raise HTTPException(422, "Invalid audio encoding") from None
    if not audio or len(audio) > 2_000_000:
        raise HTTPException(413, "Record a shorter instruction")
    try:
        client = connection.app.state.voice_http
        response = await client.post("https://api.sarvam.ai/speech-to-text", headers=headers,
                data={"model": "saaras:v3", "mode": "transcribe", "language_code": request.language_code},
                files={"file": ("instruction." + request.audio_format, audio,
                                "audio/wav" if request.audio_format == "wav" else "audio/mp4")})
        response.raise_for_status()
        transcript = response.json()["transcript"]
        if not isinstance(transcript, str) or not transcript.strip():
            raise ValueError("No speech recognized")
        return {"transcript": transcript[:2000], "language_code": request.language_code}
    except (httpx.HTTPError, ValueError, KeyError, TypeError):
        raise HTTPException(502, "Speech recognition failed. Please use text input.") from None


@router.post("/synthesize")
async def synthesize(request: SynthesisRequest, connection: Request):
    headers = key_headers()
    try:
        client = connection.app.state.voice_http
        response = await client.post("https://api.sarvam.ai/text-to-speech", headers=headers,
                json={"text": request.text, "language_code": request.language_code,
                      "model": "bulbul:v3", "speaker": "shubh", "output_audio_codec": "wav"})
        response.raise_for_status()
        audio = response.json()["audios"][0]
        if not isinstance(audio, str) or len(audio) > 8_000_000:
            raise ValueError("Invalid audio response")
        base64.b64decode(audio, validate=True)
        return {"audio_base64": audio, "mime_type": "audio/wav"}
    except (httpx.HTTPError, ValueError, KeyError, IndexError, TypeError, binascii.Error):
        raise HTTPException(502, "Speech playback is unavailable. Read the text response.") from None
