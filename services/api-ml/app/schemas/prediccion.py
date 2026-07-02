"""Schemas de entrada/salida para la predicción de agotamiento de stock (RF-IA-PRED).

El microservicio es *stateless*: recibe por cada materia prima el stock actual y la serie mensual de
ingresos (compras) y consumos (fabricaciones) que arma api-domain desde la BD, y devuelve la
tendencia + la fecha de agotamiento proyectada. No accede a la base de datos.
"""

from __future__ import annotations

from pydantic import BaseModel, Field

# Niveles de alerta (deben coincidir con el enum NivelAlertaStock de api-domain).
SIN_DATOS = "SIN_DATOS"
SIN_RIESGO = "SIN_RIESGO"
NORMAL = "NORMAL"
ATENCION = "ATENCION"
ALERTA = "ALERTA"
CRITICO = "CRITICO"

# Tendencia de la serie de existencias.
CRECIENTE = "CRECIENTE"
DECRECIENTE = "DECRECIENTE"
ESTABLE = "ESTABLE"

MODELO = "PROMEDIO_NETO_MENSUAL_v1"


class PuntoMensual(BaseModel):
    """Actividad de una materia prima en un mes (kilos)."""

    mes: str  # "YYYY-MM"
    ingresos: float = Field(ge=0)  # kg comprados (compras REGISTRADAS)
    consumo: float = Field(ge=0)  # kg consumidos (fabricaciones REGISTRADAS)


class ItemPrediccion(BaseModel):
    id_materia_prima: int
    stock_actual: float = Field(ge=0)
    serie_mensual: list[PuntoMensual] = Field(default_factory=list)


class PrediccionRequest(BaseModel):
    items: list[ItemPrediccion] = Field(default_factory=list)
    # Series (histórico + proyección) solo se devuelven si se piden: la tabla no las necesita.
    incluir_series: bool = True
    meses_proyeccion: int = Field(default=6, ge=1, le=24)


class PuntoSerie(BaseModel):
    mes: str  # "YYYY-MM"
    existencias: float


class PrediccionItemResponse(BaseModel):
    id_materia_prima: int
    nivel_alerta: str
    tendencia: str
    stock_actual: float
    dias_restantes: int | None = None
    # Días desde hoy hasta el agotamiento; api-domain lo convierte a fecha calendario.
    fecha_agotamiento_offset_dias: int | None = None
    neto_promedio: float
    consumo_promedio: float
    ingreso_promedio: float
    n_meses: int
    modelo_usado: str = MODELO
    serie_historica: list[PuntoSerie] | None = None
    serie_proyeccion: list[PuntoSerie] | None = None


class PrediccionResponse(BaseModel):
    predicciones: list[PrediccionItemResponse] = Field(default_factory=list)
