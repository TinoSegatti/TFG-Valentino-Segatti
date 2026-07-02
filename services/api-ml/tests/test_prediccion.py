"""Tests de la predicción de agotamiento de stock (servicio puro + endpoint con JWT s2s)."""

import time

from fastapi.testclient import TestClient
from jose import jwt

from app.core.config import settings
from app.main import app
from app.schemas.prediccion import (
    ALERTA,
    ATENCION,
    CRECIENTE,
    CRITICO,
    DECRECIENTE,
    NORMAL,
    SIN_DATOS,
    SIN_RIESGO,
    ItemPrediccion,
    PuntoMensual,
)
from app.services import prediccion as servicio

client = TestClient(app)


def _serie(pares: list[tuple[str, float, float]]) -> list[PuntoMensual]:
    """pares = [(mes, ingresos, consumo)]."""
    return [PuntoMensual(mes=m, ingresos=i, consumo=c) for m, i, c in pares]


def _item(stock: float, pares: list[tuple[str, float, float]]) -> ItemPrediccion:
    return ItemPrediccion(id_materia_prima=1, stock_actual=stock, serie_mensual=_serie(pares))


def _eval(stock, pares, incluir=True, meses=6):
    return servicio.evaluar_item(_item(stock, pares), incluir, meses)


def test_creciente_sin_riesgo_sube():
    # Todos los meses se compra más de lo que se consume -> existencias crecientes.
    r = _eval(1000, [("2026-01", 300, 100), ("2026-02", 300, 100), ("2026-03", 300, 100)])
    assert r.nivel_alerta == SIN_RIESGO
    assert r.tendencia == CRECIENTE
    assert r.dias_restantes is None
    assert r.neto_promedio == 200.0
    # La serie histórica termina en el stock actual y viene subiendo.
    assert r.serie_historica[-1].existencias == 1000
    assert r.serie_historica[0].existencias < r.serie_historica[-1].existencias
    # La proyección sigue subiendo.
    assert r.serie_proyeccion[-1].existencias > 1000


def test_decreciente_con_agotamiento_y_fecha():
    # Se consume más de lo que se compra -> baja hasta 0.
    r = _eval(600, [("2026-01", 100, 300), ("2026-02", 100, 300), ("2026-03", 100, 300)])
    assert r.tendencia == DECRECIENTE
    assert r.neto_promedio == -200.0
    # 600 / 200 = 3 meses -> 90 días.
    assert r.dias_restantes == 90
    assert r.fecha_agotamiento_offset_dias == 90
    assert r.nivel_alerta == NORMAL  # 90 >= 60
    assert r.serie_proyeccion[-1].existencias == 0.0


def test_sin_consumo_es_creciente_no_sin_datos():
    r = _eval(500, [("2026-01", 200, 0), ("2026-02", 200, 0), ("2026-03", 200, 0)])
    assert r.nivel_alerta == SIN_RIESGO
    assert r.tendencia == CRECIENTE


def test_menos_de_dos_meses_sin_datos():
    r = _eval(500, [("2026-03", 100, 50)])
    assert r.nivel_alerta == SIN_DATOS
    assert r.n_meses == 1


def test_serie_vacia_sin_datos():
    r = _eval(500, [])
    assert r.nivel_alerta == SIN_DATOS
    assert r.serie_historica == []


def test_historico_ancla_stock_actual():
    r = _eval(1000, [("2026-01", 500, 100), ("2026-02", 500, 100)])
    # net feb = 400 -> existencias ene = 1000 - 400 = 600.
    assert r.serie_historica[-1].existencias == 1000
    assert r.serie_historica[0].existencias == 600


def test_umbral_critico():
    # 100 / 300 neto = ~0.33 meses -> ~10 días -> CRITICO.
    r = _eval(100, [("2026-01", 0, 300), ("2026-02", 0, 300)])
    assert r.nivel_alerta == CRITICO
    assert r.dias_restantes < 15


def test_umbral_alerta_y_atencion():
    # neto -100/mes, stock 70 -> 0.7 mes -> 21 días -> ALERTA (<30).
    r_alerta = _eval(70, [("2026-01", 0, 100), ("2026-02", 0, 100)])
    assert r_alerta.nivel_alerta == ALERTA
    # stock 150 -> 1.5 mes -> 45 días -> ATENCION (<60).
    r_at = _eval(150, [("2026-01", 0, 100), ("2026-02", 0, 100)])
    assert r_at.nivel_alerta == ATENCION


def test_caida_lenta_capa_proyeccion_pero_calcula_fecha():
    # Caída de -100/mes con stock 5000 -> ~50 meses; la proyección se capa a 12 puntos.
    r = _eval(5000, [("2026-01", 0, 100), ("2026-02", 0, 100)])
    assert r.dias_restantes == 1500  # 50 meses * 30, calculado analíticamente
    assert len(r.serie_proyeccion) == 12  # capado
    assert r.serie_proyeccion[-1].existencias > 0  # no llega a 0 en la ventana graficada


def test_incluir_series_false_omite_series():
    r = _eval(600, [("2026-01", 100, 300), ("2026-02", 100, 300)], incluir=False)
    assert r.serie_historica is None
    assert r.serie_proyeccion is None
    assert r.nivel_alerta  # el resumen igual se calcula


# ---- endpoint con JWT s2s ----


def _token() -> str:
    now = int(time.time())
    return jwt.encode(
        {"iss": settings.allowed_issuer, "aud": settings.allowed_audience, "iat": now, "exp": now + 60},
        settings.jwt_ml_secret,
        algorithm="HS256",
    )


def test_endpoint_requiere_jwt():
    resp = client.post("/api/ml/prediccion/stock", json={"items": []})
    assert resp.status_code in (401, 403)


def test_endpoint_lote_ok():
    body = {
        "items": [
            {
                "id_materia_prima": 42,
                "stock_actual": 600,
                "serie_mensual": [
                    {"mes": "2026-01", "ingresos": 100, "consumo": 300},
                    {"mes": "2026-02", "ingresos": 100, "consumo": 300},
                    {"mes": "2026-03", "ingresos": 100, "consumo": 300},
                ],
            }
        ],
        "incluir_series": True,
    }
    resp = client.post(
        "/api/ml/prediccion/stock", json=body, headers={"Authorization": f"Bearer {_token()}"}
    )
    assert resp.status_code == 200
    pred = resp.json()["predicciones"][0]
    assert pred["id_materia_prima"] == 42
    assert pred["tendencia"] == DECRECIENTE
    assert pred["dias_restantes"] == 90
