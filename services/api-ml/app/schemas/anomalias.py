"""Schemas de entrada/salida para la detección de anomalías de precio (RF-IA-ANOM-*).

El microservicio es *stateless*: recibe el historial de precios en el request (lo arma api-domain
desde `t_registro_precio`) y devuelve la clasificación. No accede a la base de datos.
"""

from __future__ import annotations

from datetime import date

from pydantic import BaseModel, Field

# Clasificaciones (deben coincidir con el enum ClasificacionAnomalia de api-domain).
SIN_HISTORIAL = "SIN_HISTORIAL"
NORMAL = "NORMAL"
ATENCION = "ATENCION"
ANOMALIA_ALTA = "ANOMALIA_ALTA"

# Ventana de comparación usada para el cálculo.
VENTANA_ESTACIONAL = "ESTACIONAL"
VENTANA_GLOBAL = "GLOBAL"
VENTANA_GLOBAL_PERMISIVA = "GLOBAL_PERMISIVA"
VENTANA_NINGUNA = "NINGUNA"


class PuntoHistorial(BaseModel):
    """Un precio histórico de la materia prima con su fecha de negocio (factura)."""

    fecha: date
    precio: float = Field(ge=0)


class EvaluarAnomaliaRequest(BaseModel):
    precio_ingresado: float = Field(gt=0)
    historial: list[PuntoHistorial] = Field(default_factory=list)
    # Mes (1-12) de la compra evaluada; habilita la comparación estacional (mismo mes de años previos).
    mes_referencia: int | None = Field(default=None, ge=1, le=12)


class AnomaliaResponse(BaseModel):
    clasificacion: str
    z_score: float | None = None
    promedio_historico: float | None = None
    min_historico: float | None = None
    max_historico: float | None = None
    desviacion_pct: float | None = None
    n_muestras: int = 0
    ventana: str = VENTANA_NINGUNA
