"""Predicción de agotamiento de stock por promedio de flujo neto mensual (RF-IA-PRED).

Lógica pura, sin FastAPI ni BD. Por cada materia prima recibe el stock actual y la serie mensual de
ingresos (compras) y consumo (fabricaciones), y devuelve:

- la **serie histórica de existencias** reconstruida hacia atrás desde el stock actual (para el gráfico),
- la **proyección** hacia adelante usando el neto mensual promedio,
- la **fecha de agotamiento** (en días desde hoy) y el nivel de alerta.

Regla (método "promedio neto mensual"):
- net(m) = ingresos(m) − consumo(m);  neto_promedio = media de net sobre la ventana.
- neto_promedio ≥ 0  → SIN_RIESGO (nunca se agota; la serie sube o se mantiene).
- neto_promedio < 0  → días = stock_actual / |neto_promedio| · 30; el nivel sale de esos días.
- < 2 meses de serie → SIN_DATOS (no hay tendencia estimable).
"""

from __future__ import annotations

from math import ceil

from app.schemas.prediccion import (
    ALERTA,
    ATENCION,
    CRECIENTE,
    CRITICO,
    DECRECIENTE,
    ESTABLE,
    MODELO,
    NORMAL,
    SIN_DATOS,
    SIN_RIESGO,
    ItemPrediccion,
    PrediccionItemResponse,
    PuntoSerie,
)

MIN_MESES = 2
DIAS_POR_MES = 30
# Tope de puntos de la proyección decreciente: para caídas muy lentas el agotamiento puede estar a
# años, y graficar decenas de puntos ensucia el popup. La fecha/días de agotamiento se calculan igual
# de forma analítica; el gráfico solo muestra la pendiente de los próximos meses.
MAX_MESES_PROYECCION_DECRECIENTE = 12
# Umbrales de días restantes para el nivel de alerta.
DIAS_CRITICO = 15
DIAS_ALERTA = 30
DIAS_ATENCION = 60


def evaluar_lote(
    items: list[ItemPrediccion], incluir_series: bool, meses_proyeccion: int
) -> list[PrediccionItemResponse]:
    return [evaluar_item(it, incluir_series, meses_proyeccion) for it in items]


def evaluar_item(
    item: ItemPrediccion, incluir_series: bool, meses_proyeccion: int
) -> PrediccionItemResponse:
    serie = sorted(item.serie_mensual, key=lambda p: p.mes)
    n = len(serie)
    stock = item.stock_actual

    if n < MIN_MESES:
        return PrediccionItemResponse(
            id_materia_prima=item.id_materia_prima,
            nivel_alerta=SIN_DATOS,
            tendencia=ESTABLE,
            stock_actual=stock,
            neto_promedio=0.0,
            consumo_promedio=0.0,
            ingreso_promedio=0.0,
            n_meses=n,
            serie_historica=_historico(serie, stock) if incluir_series else None,
            serie_proyeccion=[] if incluir_series else None,
        )

    nets = [p.ingresos - p.consumo for p in serie]
    neto_promedio = sum(nets) / n
    consumo_promedio = sum(p.consumo for p in serie) / n
    ingreso_promedio = sum(p.ingresos for p in serie) / n
    ultimo_mes = serie[-1].mes

    if neto_promedio >= 0:
        nivel = SIN_RIESGO
        tendencia = CRECIENTE if neto_promedio > 0 else ESTABLE
        dias: int | None = None
        proyeccion = (
            _proyeccion_creciente(ultimo_mes, stock, neto_promedio, meses_proyeccion)
            if incluir_series
            else None
        )
    else:
        tendencia = DECRECIENTE
        meses_hasta = stock / (-neto_promedio)  # stock ≥ 0, neto_promedio < 0
        dias = round(meses_hasta * DIAS_POR_MES)
        nivel = _nivel_por_dias(dias)
        proyeccion = (
            _proyeccion_decreciente(ultimo_mes, stock, neto_promedio)
            if incluir_series
            else None
        )

    return PrediccionItemResponse(
        id_materia_prima=item.id_materia_prima,
        nivel_alerta=nivel,
        tendencia=tendencia,
        stock_actual=round(stock, 3),
        dias_restantes=dias,
        fecha_agotamiento_offset_dias=dias,
        neto_promedio=round(neto_promedio, 3),
        consumo_promedio=round(consumo_promedio, 3),
        ingreso_promedio=round(ingreso_promedio, 3),
        n_meses=n,
        modelo_usado=MODELO,
        serie_historica=_historico(serie, stock) if incluir_series else None,
        serie_proyeccion=proyeccion,
    )


def _nivel_por_dias(dias: int) -> str:
    if dias < DIAS_CRITICO:
        return CRITICO
    if dias < DIAS_ALERTA:
        return ALERTA
    if dias < DIAS_ATENCION:
        return ATENCION
    return NORMAL


def _historico(serie, stock: float) -> list[PuntoSerie]:
    """Existencias al cierre de cada mes, reconstruidas hacia atrás desde el stock actual."""
    n = len(serie)
    if n == 0:
        return []
    nets = [p.ingresos - p.consumo for p in serie]
    fin = [0.0] * n
    fin[-1] = stock
    for i in range(n - 2, -1, -1):
        fin[i] = fin[i + 1] - nets[i + 1]
    return [PuntoSerie(mes=serie[i].mes, existencias=round(fin[i], 3)) for i in range(n)]


def _proyeccion_creciente(
    ultimo_mes: str, stock: float, neto: float, meses: int
) -> list[PuntoSerie]:
    return [
        PuntoSerie(mes=_sumar_meses(ultimo_mes, t), existencias=round(stock + neto * t, 3))
        for t in range(1, meses + 1)
    ]


def _proyeccion_decreciente(ultimo_mes: str, stock: float, neto: float) -> list[PuntoSerie]:
    meses_hasta = stock / (-neto) if neto != 0 else 0.0
    n_puntos = max(1, min(ceil(meses_hasta), MAX_MESES_PROYECCION_DECRECIENTE))
    puntos = []
    for t in range(1, n_puntos + 1):
        val = max(0.0, stock + neto * t)
        puntos.append(PuntoSerie(mes=_sumar_meses(ultimo_mes, t), existencias=round(val, 3)))
    return puntos


def _sumar_meses(mes: str, k: int) -> str:
    anio, m = mes.split("-")
    total = int(anio) * 12 + (int(m) - 1) + k
    return f"{total // 12:04d}-{total % 12 + 1:02d}"
