"""Proves de l'API del NAS.

Executa-les des de la carpeta server/:
    pip install -r requirements-dev.txt
    pytest
"""
import importlib
import sys
from pathlib import Path

import pytest
from fastapi.testclient import TestClient

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))


@pytest.fixture()
def entorn(tmp_path, monkeypatch):
    dades = tmp_path / "data"
    dades.mkdir()
    monkeypatch.setenv("NAS_STORAGE_PATH", str(dades))
    monkeypatch.setenv("DB_NAME", str(tmp_path / "test.db"))
    monkeypatch.delenv("NAS_SECRET_KEY", raising=False)
    monkeypatch.chdir(tmp_path)
    sys.modules.pop("main", None)
    main = importlib.import_module("main")
    client = TestClient(main.app)
    yield main, client, dades
    main.db.close()


def login(client, usuari, contrasenya):
    r = client.post("/login", data={"username": usuari, "password": contrasenya})
    assert r.status_code == 200, r.text
    return {"Authorization": f"Bearer {r.json()['access_token']}"}


@pytest.fixture()
def admin(entorn):
    main, client, dades = entorn
    assert client.post("/api/system/setup", json={"admin_username": "nil", "admin_password": "secret"}).status_code == 200
    return main, client, dades, login(client, "nil", "secret")


@pytest.fixture()
def usuari(admin):
    main, client, dades, h_admin = admin
    r = client.post("/api/admin/users", headers=h_admin, json={"username": "anna", "password": "pw", "role": "user"})
    assert r.status_code == 200
    return main, client, dades, h_admin, login(client, "anna", "pw")


# ---------- Setup i autenticació ----------
def test_setup_complet(admin):
    _, client, _, h = admin
    assert client.get("/api/system/setup-status").json() == {"setup_completed": True}
    assert client.get("/api/system/status", headers=h).status_code == 200


def test_admin_admin_nomes_abans_del_setup(entorn):
    _, client, _ = entorn
    h_provisional = login(client, "admin", "admin")
    client.post("/api/system/setup", json={"admin_username": "nil", "admin_password": "secret"})
    # Després del setup, ni el login provisional ni el token provisional serveixen
    assert client.post("/login", data={"username": "admin", "password": "admin"}).status_code == 401
    assert client.get("/api/admin/users", headers=h_provisional).status_code == 401


def test_clau_jwt_aleatoria_i_persistent(entorn):
    main, _, _ = entorn
    assert len(main.SECRET_KEY) >= 32
    assert main.SECRET_KEY == main.obtenir_secret_key()


def test_token_falsificat_rebutjat(admin):
    import jwt
    _, client, _, _ = admin
    fals = jwt.encode({"sub": "nil"}, "nas-secret-key-default-12345", algorithm="HS256")
    assert client.get("/api/system/status", headers={"Authorization": f"Bearer {fals}"}).status_code == 401


# ---------- Rutes fora de la carpeta del NAS ----------
@pytest.mark.parametrize("nom", ["../fora", "a/b", "..", "", "  "])
def test_mkdir_rebutja_noms_perillosos(admin, nom):
    _, client, _, h = admin
    r = client.post("/api/files/mkdir", headers=h, json={"nom_carpeta": nom})
    assert r.status_code == 400


def test_upload_no_surt_de_la_carpeta(admin, tmp_path):
    _, client, dades, h = admin
    r = client.post("/api/files/upload", headers=h, data={"path": ""}, files={"file": ("../../escapat.txt", b"x")})
    assert r.status_code == 200
    assert (dades / "escapat.txt").exists()
    assert not (tmp_path / "escapat.txt").exists()


def test_path_traversal_a_la_ruta(admin):
    _, client, _, h = admin
    assert client.get("/api/files/list", headers=h, params={"path": "../.."}).status_code == 403


# ---------- Permisos ----------
def test_usuari_pot_fer_backup(usuari):
    _, client, dades, _, h = usuari
    assert client.post("/api/files/mkdir", headers=h, json={"nom_carpeta": "backups"}).status_code == 200
    r = client.post("/api/files/upload", headers=h, data={"path": "backups"}, files={"file": ("foto.jpg", b"img")})
    assert r.status_code == 200
    assert (dades / "backups" / "foto.jpg").read_bytes() == b"img"


@pytest.mark.parametrize("peticio", [
    ("get", "/api/files/list", {"params": {"path": ""}}),
    ("delete", "/api/files/delete", {"params": {"path": "x"}}),
    ("put", "/api/files/rename", {"json": {"path": "x", "nou_nom": "y"}}),
    ("get", "/api/files/download", {"params": {"path": "x"}}),
    ("get", "/api/admin/users", {}),
])
def test_usuari_no_pot_fer_operacions_d_admin(usuari, peticio):
    _, client, dades, _, h = usuari
    (dades / "x").write_text("x")
    metode, url, kwargs = peticio
    assert getattr(client, metode)(url, headers=h, **kwargs).status_code == 403


def test_no_es_pot_treure_l_ultim_admin(admin):
    _, client, _, h = admin
    r = client.put("/api/admin/users/nil", headers=h, json={"role": "user"})
    assert r.status_code == 400


def test_rol_invalid_rebutjat(admin):
    _, client, _, h = admin
    r = client.post("/api/admin/users", headers=h, json={"username": "x", "password": "y", "role": "superadmin"})
    assert r.status_code == 422


# ---------- Gestor de fitxers ----------
def test_cicle_complet_de_fitxers(admin):
    _, client, dades, h = admin
    client.post("/api/files/upload", headers=h, data={"path": "docs"}, files={"file": ("a.txt", b"hola")})
    llista = client.get("/api/files/list", headers=h, params={"path": "docs"}).json()["contingut"]
    assert [x["nom"] for x in llista] == ["a.txt"]

    assert client.put("/api/files/rename", headers=h, json={"path": "docs/a.txt", "nou_nom": "b.txt"}).status_code == 200
    assert client.put("/api/files/rename", headers=h, json={"path": "docs/b.txt", "nou_nom": "../c.txt"}).status_code == 400

    r = client.get("/api/files/download", headers=h, params={"path": "docs/b.txt"})
    assert r.status_code == 200 and r.content == b"hola"
    r = client.get("/api/files/download", headers=h, params={"path": "docs"})
    assert r.status_code == 200 and r.content[:2] == b"PK"  # zip

    assert client.delete("/api/files/delete", headers=h, params={"path": "docs/b.txt"}).status_code == 200
    assert client.delete("/api/files/delete", headers=h, params={"path": "docs/b.txt"}).status_code == 404
    assert client.delete("/api/files/delete", headers=h, params={"path": "/"}).status_code == 403


def test_noms_duplicats(admin):
    _, client, _, h = admin
    for _ in range(2):
        r = client.post("/api/files/upload", headers=h, data={"path": ""}, files={"file": ("a.txt", b"1")})
    assert r.json()["missatge"].endswith("a (1).txt")


def test_logs(admin):
    _, client, _, h = admin
    entrades = [{"timestamp": str(i), "level": "I", "tag": "t", "message": f"m{i}"} for i in range(150)]
    assert client.post("/api/system/logs", headers=h, json=entrades).status_code == 200
    logs = client.get("/api/system/logs", headers=h).json()
    assert len(logs) == 100 and logs[0]["message"] == "m149"
