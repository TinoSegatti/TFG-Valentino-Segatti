from fastapi.testclient import TestClient

from app.main import app

client = TestClient(app)


def test_health():
    r = client.get("/api/ml/health")
    assert r.status_code == 200
    assert r.json()["status"] == "ok"
