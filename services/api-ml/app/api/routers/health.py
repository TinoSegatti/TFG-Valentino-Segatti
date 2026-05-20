from fastapi import APIRouter

router = APIRouter()


@router.get("/health")
def health():
    return {
        "status": "ok",
        "service": "api-ml",
        "version": "0.1.0",
        "modelos_cargados": [],
    }
