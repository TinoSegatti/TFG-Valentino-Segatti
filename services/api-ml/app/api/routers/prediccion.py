"""Endpoint de predicción de agotamiento de stock (RF-IA-PRED).

Protegido por JWT s2s (ADR-0004): solo api-domain lo invoca. El servicio es stateless; recibe el
stock actual y la serie mensual (ingresos/consumo) de cada materia prima y devuelve la tendencia +
la proyección de agotamiento.
"""

from fastapi import APIRouter, Depends

from app.core.security import verificar_jwt_s2s
from app.schemas.prediccion import PrediccionRequest, PrediccionResponse
from app.services import prediccion as servicio

router = APIRouter(prefix="/prediccion", tags=["prediccion"])


@router.post("/stock", response_model=PrediccionResponse)
def predecir_stock(
    request: PrediccionRequest,
    _claims: dict = Depends(verificar_jwt_s2s),
) -> PrediccionResponse:
    """Proyecta el agotamiento de stock por materia prima (lote)."""
    predicciones = servicio.evaluar_lote(
        request.items, request.incluir_series, request.meses_proyeccion
    )
    return PrediccionResponse(predicciones=predicciones)
