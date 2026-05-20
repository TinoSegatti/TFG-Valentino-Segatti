from fastapi import FastAPI

from app.api.routers import anomalias, health

app = FastAPI(
    title="REFORMA api-ml",
    description="Predicciones y detección de anomalías",
    version="0.1.0",
)

app.include_router(health.router, prefix="/api/ml", tags=["health"])
app.include_router(anomalias.router, prefix="/api/ml")
