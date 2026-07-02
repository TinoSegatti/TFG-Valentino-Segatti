"""Detección de anomalías de precio por Z-Score (RF-IA-ANOM-002 / 003 / 005).

Lógica pura, sin FastAPI ni BD: recibe el precio ingresado y la serie histórica de precios de la
materia prima y devuelve la clasificación. Regla (spec §15):

- < 3 compras previas .................... SIN_HISTORIAL (no se compara)
- Z ≤ 1.5 ................................. NORMAL
- 1.5 < Z ≤ umbral_alto .................. ATENCION
- Z > umbral_alto ........................ ANOMALIA_ALTA

Estacionalidad (RF-IA-ANOM-005):
- Con > 6 meses de historial se compara contra los precios del MISMO mes (años previos) y se usan
  los umbrales estándar (alto = 2.5).
- Con historial insuficiente (≤ 6 meses) se usa el promedio global con un umbral más permisivo
  (alto = 3.0) para evitar falsos positivos por alta volatilidad.
"""

from __future__ import annotations

from datetime import date

import numpy as np

from app.schemas.anomalias import (
    ANOMALIA_ALTA,
    ATENCION,
    NORMAL,
    SIN_HISTORIAL,
    VENTANA_ESTACIONAL,
    VENTANA_GLOBAL,
    VENTANA_GLOBAL_PERMISIVA,
    VENTANA_NINGUNA,
    AnomaliaResponse,
    PuntoHistorial,
)

MIN_MUESTRAS = 3
DIAS_SEIS_MESES = 183
Z_NORMAL = 1.5
Z_ALTO_ESTANDAR = 2.5
Z_ALTO_PERMISIVO = 3.0


def evaluar(
    precio_ingresado: float,
    historial: list[PuntoHistorial],
    mes_referencia: int | None = None,
) -> AnomaliaResponse:
    if len(historial) < MIN_MUESTRAS:
        return AnomaliaResponse(
            clasificacion=SIN_HISTORIAL,
            n_muestras=len(historial),
            ventana=VENTANA_NINGUNA,
        )

    muestra, ventana = _seleccionar_muestra(historial, mes_referencia)
    precios = np.array([p.precio for p in muestra], dtype=float)

    media = float(np.mean(precios))
    # Desvío muestral (ddof=1): comparamos un precio nuevo contra una muestra, no contra la población.
    desvio = float(np.std(precios, ddof=1)) if len(precios) > 1 else 0.0

    z = 0.0 if desvio == 0.0 else (precio_ingresado - media) / desvio
    desviacion_pct = 0.0 if media == 0.0 else ((precio_ingresado - media) / media) * 100.0

    umbral_alto = Z_ALTO_PERMISIVO if ventana == VENTANA_GLOBAL_PERMISIVA else Z_ALTO_ESTANDAR
    clasificacion = _clasificar(z, umbral_alto)

    return AnomaliaResponse(
        clasificacion=clasificacion,
        z_score=round(z, 3),
        promedio_historico=round(media, 3),
        min_historico=round(float(np.min(precios)), 3),
        max_historico=round(float(np.max(precios)), 3),
        desviacion_pct=round(desviacion_pct, 2),
        n_muestras=len(muestra),
        ventana=ventana,
    )


def _seleccionar_muestra(
    historial: list[PuntoHistorial], mes_referencia: int | None
) -> tuple[list[PuntoHistorial], str]:
    """Decide la ventana de comparación y devuelve (muestra, etiqueta de ventana)."""
    if _tiene_seis_meses(historial):
        if mes_referencia is not None:
            estacional = [p for p in historial if p.fecha.month == mes_referencia]
            if len(estacional) >= MIN_MUESTRAS:
                return estacional, VENTANA_ESTACIONAL
        # Hay >6 meses pero no suficientes del mismo mes: global con umbrales estándar.
        return historial, VENTANA_GLOBAL
    # Historial corto: global con umbral permisivo.
    return historial, VENTANA_GLOBAL_PERMISIVA


def _tiene_seis_meses(historial: list[PuntoHistorial]) -> bool:
    fechas: list[date] = [p.fecha for p in historial]
    return (max(fechas) - min(fechas)).days > DIAS_SEIS_MESES


def _clasificar(z: float, umbral_alto: float) -> str:
    if z > umbral_alto:
        return ANOMALIA_ALTA
    if z > Z_NORMAL:
        return ATENCION
    return NORMAL
