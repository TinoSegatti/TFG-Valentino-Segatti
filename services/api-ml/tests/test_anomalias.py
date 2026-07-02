"""Tests de la detección de anomalías de precio (servicio puro + endpoint con JWT s2s)."""

from datetime import date

from fastapi.testclient import TestClient
from jose import jwt

from app.core.config import settings
from app.main import app
from app.schemas.anomalias import (
    ANOMALIA_ALTA,
    ATENCION,
    NORMAL,
    SIN_HISTORIAL,
    VENTANA_ESTACIONAL,
    VENTANA_GLOBAL,
    VENTANA_GLOBAL_PERMISIVA,
    PuntoHistorial,
)
from app.services import anomalias as servicio

client = TestClient(app)


def _hist(pares: list[tuple[date, float]]) -> list[PuntoHistorial]:
    return [PuntoHistorial(fecha=f, precio=p) for f, p in pares]


def _corto(precios: list[float]) -> list[PuntoHistorial]:
    """Historial de pocos días (no llega a 6 meses) -> ventana global permisiva."""
    return _hist([(date(2026, 1, 1 + i), p) for i, p in enumerate(precios)])


# --- Servicio puro -----------------------------------------------------------------------------

def test_sin_historial_suficiente():
    r = servicio.evaluar(100.0, _corto([100.0, 105.0]))
    assert r.clasificacion == SIN_HISTORIAL
    assert r.n_muestras == 2


def test_precio_normal():
    r = servicio.evaluar(100.0, _corto([98.0, 100.0, 102.0, 100.0, 100.0]))
    assert r.clasificacion == NORMAL
    assert r.ventana == VENTANA_GLOBAL_PERMISIVA


def test_precio_atencion_permisivo():
    # media 100, desvío muestral ~1.414; precio 103 -> z ~2.12 (entre 1.5 y 3.0 permisivo)
    r = servicio.evaluar(103.0, _corto([98.0, 100.0, 102.0, 100.0, 100.0]))
    assert r.clasificacion == ATENCION


def test_precio_anomalia_alta_permisivo():
    # precio 106 -> z ~4.24 > 3.0
    r = servicio.evaluar(106.0, _corto([98.0, 100.0, 102.0, 100.0, 100.0]))
    assert r.clasificacion == ANOMALIA_ALTA
    assert r.desviacion_pct == 6.0


def test_desvio_cero_no_divide():
    r = servicio.evaluar(200.0, _corto([100.0, 100.0, 100.0]))
    assert r.clasificacion == NORMAL
    assert r.z_score == 0.0


def test_estacional_usa_mismo_mes_y_umbral_estandar():
    historial = _hist(
        [
            (date(2024, 1, 15), 100.0),  # otro mes: asegura span > 6 meses
            (date(2024, 6, 10), 90.0),
            (date(2025, 6, 10), 100.0),
            (date(2026, 6, 10), 110.0),
        ]
    )
    # subset junio = [90,100,110]: media 100, desvío 10; precio 126 -> z 2.6 > 2.5 (estándar)
    r = servicio.evaluar(126.0, historial, mes_referencia=6)
    assert r.ventana == VENTANA_ESTACIONAL
    assert r.n_muestras == 3
    assert r.clasificacion == ANOMALIA_ALTA


def test_estacional_sin_muestras_del_mes_cae_a_global():
    historial = _hist(
        [
            (date(2024, 1, 15), 100.0),
            (date(2024, 6, 10), 90.0),
            (date(2025, 6, 10), 100.0),
            (date(2026, 6, 10), 110.0),
        ]
    )
    # marzo no tiene >=3 muestras: cae a global con umbrales estándar
    r = servicio.evaluar(105.0, historial, mes_referencia=3)
    assert r.ventana == VENTANA_GLOBAL


# --- Endpoint + JWT s2s ------------------------------------------------------------------------

def _token(secret: str = None) -> str:
    return jwt.encode(
        {"iss": settings.allowed_issuer, "aud": settings.allowed_audience, "sub": "api-domain"},
        secret or settings.jwt_ml_secret,
        algorithm="HS256",
    )


_BODY = {
    "precio_ingresado": 126.0,
    "historial": [
        {"fecha": "2024-06-10", "precio": 90.0},
        {"fecha": "2025-06-10", "precio": 100.0},
        {"fecha": "2026-06-10", "precio": 110.0},
    ],
}


def test_endpoint_sin_token_rechaza():
    r = client.post("/api/ml/anomalias/evaluar", json=_BODY)
    assert r.status_code == 403  # HTTPBearer: falta credencial


def test_endpoint_token_invalido():
    r = client.post(
        "/api/ml/anomalias/evaluar",
        json=_BODY,
        headers={"Authorization": f"Bearer {_token('secreto_equivocado_pero_largo_para_hs256_xxxx')}"},
    )
    assert r.status_code == 401


def test_endpoint_ok():
    r = client.post(
        "/api/ml/anomalias/evaluar",
        json=_BODY,
        headers={"Authorization": f"Bearer {_token()}"},
    )
    assert r.status_code == 200
    assert r.json()["clasificacion"] in {NORMAL, ATENCION, ANOMALIA_ALTA}
