"""Verificación del JWT servicio-a-servicio api-domain -> api-ml (ADR-0004).

api-domain firma con `JWT_ML_SECRET` (HS256), claims `iss=api-domain`, `aud=api-ml`. El navegador
nunca llama a api-ml directamente; todo pasa por api-domain.
"""

from __future__ import annotations

from fastapi import Depends, HTTPException, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from jose import JWTError, jwt

from app.core.config import settings

_bearer = HTTPBearer(auto_error=True)


def verificar_jwt_s2s(
    credenciales: HTTPAuthorizationCredentials = Depends(_bearer),
) -> dict:
    """Valida el token s2s. Lanza 401 si la firma, el emisor o la audiencia no coinciden."""
    try:
        return jwt.decode(
            credenciales.credentials,
            settings.jwt_ml_secret,
            algorithms=["HS256"],
            issuer=settings.allowed_issuer,
            audience=settings.allowed_audience,
        )
    except JWTError as exc:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Token s2s inválido",
        ) from exc
