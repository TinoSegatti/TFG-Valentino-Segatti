"""Endpoints de detección de anomalías de precio (RF-IA-ANOM-*).

Protegidos por JWT s2s (ADR-0004): solo api-domain los invoca. El servicio es stateless; recibe el
historial en el request y devuelve la clasificación.
"""

from fastapi import APIRouter, Depends

from app.core.security import verificar_jwt_s2s
from app.schemas.anomalias import AnomaliaResponse, EvaluarAnomaliaRequest
from app.services import anomalias as servicio

router = APIRouter(prefix="/anomalias", tags=["anomalias"])


@router.post("/evaluar", response_model=AnomaliaResponse)
def evaluar_anomalia(
    request: EvaluarAnomaliaRequest,
    _claims: dict = Depends(verificar_jwt_s2s),
) -> AnomaliaResponse:
    """Evalúa un precio contra el historial de la materia prima y lo clasifica por Z-Score."""
    return servicio.evaluar(
        precio_ingresado=request.precio_ingresado,
        historial=request.historial,
        mes_referencia=request.mes_referencia,
    )
