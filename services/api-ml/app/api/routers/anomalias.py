from fastapi import APIRouter

router = APIRouter(prefix="/anomalias", tags=["anomalias"])


@router.post("/precios-compras")
def evaluar_precios_compra():
    """Placeholder Sprint 3 — IsolationForest / z-score (RF-IA-ANOM-*)."""
    return {"mensaje": "pendiente de implementación", "anomalias": []}
