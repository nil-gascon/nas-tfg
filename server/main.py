"""
API REST del NAS domèstic.

Exposa l'autenticació (JWT), la gestió d'usuaris, la gestió de fitxers
dins de NAS_STORAGE_PATH i la informació d'estat del sistema per a
l'aplicació Android.
"""
import asyncio
import json
import os
import secrets
import shutil
import tempfile
from collections import deque
from datetime import datetime, timedelta, timezone
from hashlib import sha256
from pathlib import Path
from typing import List, Literal, Optional
from urllib.parse import unquote

import bcrypt
import jwt
import psutil
from dotenv import load_dotenv
from fastapi import Body, Depends, FastAPI, File, Form, HTTPException, Query, Request, UploadFile
from fastapi.responses import FileResponse, StreamingResponse
from fastapi.security import OAuth2PasswordBearer, OAuth2PasswordRequestForm
from peewee import BooleanField, CharField, DateTimeField, Model, SqliteDatabase
from pydantic import BaseModel
from starlette.background import BackgroundTask

# --------------------------------------------------------------------------
# Configuració
# --------------------------------------------------------------------------
BASE_DIR = Path(__file__).parent
load_dotenv(dotenv_path=BASE_DIR / ".env")

ALGORITHM = os.getenv("ALGORITHM", "HS256")
TOKEN_EXPIRATION_MINUTES = int(os.getenv("TOKEN_EXPIRATION_MINUTES", "60"))
NAS_STORAGE_PATH = Path(os.getenv("NAS_STORAGE_PATH", "/NAS/data")).resolve()
DB_NAME = os.getenv("DB_NAME", "nas_users.db")
APP_LOGS_FILE = NAS_STORAGE_PATH / os.getenv("APP_LOGS_FILE", "app_system_logs.jsonl")

# La primera lectura de psutil.cpu_percent sempre retorna 0; la fem ara
# perquè les següents ja donin valors reals.
psutil.cpu_percent(interval=None)
psutil.cpu_percent(interval=None, percpu=True)

# --------------------------------------------------------------------------
# Base de dades
# --------------------------------------------------------------------------
db = SqliteDatabase(DB_NAME)


class BaseModelDB(Model):
    class Meta:
        database = db


class User(BaseModelDB):
    username = CharField(unique=True)
    hashed_password = CharField()
    role = CharField(default="user")
    is_active = BooleanField(default=True)
    created_at = DateTimeField(default=datetime.now)


class SystemConfig(BaseModelDB):
    key = CharField(unique=True)
    value = CharField()


db.connect()
db.create_tables([User, SystemConfig])


def obtenir_secret_key() -> str:
    """Clau per signar els JWT.

    Es fa servir NAS_SECRET_KEY si està definida. Si no, se'n genera una
    d'aleatòria la primera vegada i es guarda a la base de dades, de manera
    que no hi ha mai cap clau per defecte coneguda.
    """
    clau_env = os.getenv("NAS_SECRET_KEY")
    if clau_env:
        return clau_env
    config = SystemConfig.get_or_none(SystemConfig.key == "jwt_secret")
    if config is None:
        config = SystemConfig.create(key="jwt_secret", value=secrets.token_urlsafe(48))
    return config.value


SECRET_KEY = obtenir_secret_key()


def is_setup_completed() -> bool:
    config = SystemConfig.get_or_none(SystemConfig.key == "setup_completed")
    return config is not None and config.value == "true"


# --------------------------------------------------------------------------
# Esquemes de dades
# --------------------------------------------------------------------------
Rol = Literal["admin", "user"]


class UserOut(BaseModel):
    username: str
    role: str
    created_at: datetime


class UserCreate(BaseModel):
    username: str
    password: str
    role: Rol = "user"


class UserUpdate(BaseModel):
    username: Optional[str] = None
    password: Optional[str] = None
    role: Optional[Rol] = None


class SetupRequest(BaseModel):
    admin_username: str
    admin_password: str


class PasswordChangeRequest(BaseModel):
    old_password: str
    new_password: str


class LogEntry(BaseModel):
    timestamp: str
    level: str
    tag: str
    message: str


class MkdirRequest(BaseModel):
    nom_carpeta: str
    path: str = ""
    mtime: Optional[float] = None
    allow_reuse: Optional[bool] = False


class RenameRequest(BaseModel):
    path: str
    nou_nom: str


# --------------------------------------------------------------------------
# Seguretat: contrasenyes, tokens i permisos
# --------------------------------------------------------------------------
def get_password_hash(password: str) -> str:
    # Es fa un SHA-256 previ perquè bcrypt només admet 72 bytes d'entrada.
    pre_hash = sha256(password.encode("utf-8")).hexdigest().encode("utf-8")
    return bcrypt.hashpw(pre_hash, bcrypt.gensalt()).decode("utf-8")


def verify_password(plain_password: str, hashed_password: str) -> bool:
    pre_hash = sha256(plain_password.encode("utf-8")).hexdigest().encode("utf-8")
    hash_bytes = hashed_password.encode("utf-8")
    try:
        if bcrypt.checkpw(pre_hash, hash_bytes):
            return True
        # Compatibilitat amb usuaris creats amb versions antigues, que
        # aplicaven bcrypt directament sobre la contrasenya.
        return bcrypt.checkpw(plain_password.encode("utf-8"), hash_bytes)
    except ValueError:
        return False


def crear_token_jwt(data: dict) -> str:
    to_encode = data.copy()
    to_encode["exp"] = datetime.now(timezone.utc) + timedelta(minutes=TOKEN_EXPIRATION_MINUTES)
    return jwt.encode(to_encode, SECRET_KEY, algorithm=ALGORITHM)


def usuari_des_de_token(token: str) -> User:
    """Valida un JWT i retorna l'usuari corresponent."""
    try:
        payload = jwt.decode(token, SECRET_KEY, algorithms=[ALGORITHM])
    except jwt.PyJWTError:
        raise HTTPException(status_code=401, detail="Token invàlid o expirat")
    username = payload.get("sub")
    if username is None:
        raise HTTPException(status_code=401, detail="Token invàlid")
    # Token provisional del primer accés (admin/admin). Només és vàlid
    # mentre el setup inicial no s'ha completat.
    if payload.get("setup_pending"):
        if is_setup_completed() or username != "admin":
            raise HTTPException(status_code=401, detail="Token invàlid o expirat")
        return User(username="admin", role="admin")
    user = User.get_or_none(User.username == username)
    if user is None or not user.is_active:
        raise HTTPException(status_code=401, detail="Usuari no trobat")
    return user


oauth2_scheme = OAuth2PasswordBearer(tokenUrl="login")


def obtenir_usuari_actual(token: str = Depends(oauth2_scheme)) -> User:
    return usuari_des_de_token(token)


def verificar_admin(user: User = Depends(obtenir_usuari_actual)) -> User:
    if user.role != "admin":
        raise HTTPException(status_code=403, detail="Es requereixen permisos d'administrador")
    return user


def comptar_admins() -> int:
    return User.select().where(User.role == "admin").count()


# --------------------------------------------------------------------------
# Utilitats de fitxers
# --------------------------------------------------------------------------
def obtenir_ruta_segura(ruta_relativa: str) -> Path:
    """Converteix una ruta relativa en absoluta, sense sortir de NAS_STORAGE_PATH."""
    ruta_neta = unquote(ruta_relativa).strip().strip("/")
    ruta_final = (NAS_STORAGE_PATH / ruta_neta).resolve() if ruta_neta else NAS_STORAGE_PATH
    if not ruta_final.is_relative_to(NAS_STORAGE_PATH):
        raise HTTPException(status_code=403, detail="Accés denegat")
    return ruta_final


def validar_nom(nom: Optional[str]) -> str:
    """Comprova que un nom de fitxer o carpeta és un sol component vàlid."""
    nom = (nom or "").strip()
    if not nom or nom in (".", "..") or "/" in nom or "\\" in nom or "\x00" in nom:
        raise HTTPException(status_code=400, detail="Nom de fitxer o carpeta no vàlid")
    return nom


def obtenir_nom_unic(directori: Path, nom: str) -> str:
    """Retorna `nom`, o `nom (1)`, `nom (2)`... si ja existeix."""
    if not (directori / nom).exists():
        return nom
    stem, suffix = Path(nom).stem, Path(nom).suffix
    c = 1
    while (directori / f"{stem} ({c}){suffix}").exists():
        c += 1
    return f"{stem} ({c}){suffix}"


def esborrar_silenciosament(*rutes: str) -> None:
    for ruta in rutes:
        shutil.rmtree(ruta, ignore_errors=True)


def info_disc() -> dict:
    total, usat, lliure = shutil.disk_usage(NAS_STORAGE_PATH)
    return {
        "capacitat_total_gb": round(total / (1024 ** 3), 2),
        "espai_usat_gb": round(usat / (1024 ** 3), 2),
        "espai_lliure_gb": round(lliure / (1024 ** 3), 2),
        "percentatge_usat": round((usat / total) * 100, 1),
    }


# --------------------------------------------------------------------------
# Aplicació
# --------------------------------------------------------------------------
app = FastAPI(title="NAS API", version="2.1")


# ---------- Setup inicial i login ----------
@app.get("/api/system/setup-status")
def get_setup_status():
    return {"setup_completed": is_setup_completed()}


@app.post("/api/system/setup")
def initial_setup(req: SetupRequest):
    if is_setup_completed():
        raise HTTPException(status_code=400, detail="El setup ja ha estat realitzat")
    username = req.admin_username.strip()
    if not username or not req.admin_password:
        raise HTTPException(status_code=400, detail="Cal un nom d'usuari i una contrasenya")
    with db.atomic():
        User.create(username=username, hashed_password=get_password_hash(req.admin_password), role="admin")
        SystemConfig.create(key="setup_completed", value="true")
    token = crear_token_jwt({"sub": username, "role": "admin"})
    return {"access_token": token, "token_type": "bearer", "role": "admin"}


@app.post("/login")
def login(form_data: OAuth2PasswordRequestForm = Depends()):
    if not is_setup_completed():
        # Abans del setup només s'accepta l'accés provisional admin/admin.
        if form_data.username == "admin" and form_data.password == "admin":
            token = crear_token_jwt({"sub": "admin", "role": "admin", "setup_pending": True})
            return {"access_token": token, "token_type": "bearer", "role": "admin"}
        raise HTTPException(status_code=401, detail="Credencials incorrectes")
    user = User.get_or_none(User.username == form_data.username)
    if user and user.is_active and verify_password(form_data.password, user.hashed_password):
        token = crear_token_jwt({"sub": user.username, "role": user.role})
        return {"access_token": token, "token_type": "bearer", "role": user.role}
    raise HTTPException(status_code=401, detail="Credencials incorrectes")


@app.post("/api/system/change-password")
def canviar_contrasenya(req: PasswordChangeRequest, user: User = Depends(obtenir_usuari_actual)):
    if user.id is None:
        raise HTTPException(status_code=400, detail="Cal completar el setup inicial")
    if not verify_password(req.old_password, user.hashed_password):
        raise HTTPException(status_code=400, detail="La contrasenya actual no és correcta")
    if not req.new_password:
        raise HTTPException(status_code=400, detail="La nova contrasenya no pot ser buida")
    user.hashed_password = get_password_hash(req.new_password)
    user.save()
    return {"estat": "ok", "missatge": "Contrasenya actualitzada correctament"}


# ---------- Gestió d'usuaris (només admin) ----------
@app.get("/api/admin/users", response_model=List[UserOut])
def llistar_usuaris(admin: User = Depends(verificar_admin)):
    return [UserOut(username=u.username, role=u.role, created_at=u.created_at) for u in User.select()]


@app.post("/api/admin/users")
def crear_usuari(req: UserCreate, admin: User = Depends(verificar_admin)):
    username = req.username.strip()
    if not username or not req.password:
        raise HTTPException(status_code=400, detail="Cal un nom d'usuari i una contrasenya")
    if User.get_or_none(User.username == username):
        raise HTTPException(status_code=400, detail="L'usuari ja existeix")
    User.create(username=username, hashed_password=get_password_hash(req.password), role=req.role)
    return {"estat": "ok", "missatge": "Usuari creat"}


@app.put("/api/admin/users/{username}")
def editar_usuari(username: str, req: UserUpdate, admin: User = Depends(verificar_admin)):
    user = User.get_or_none(User.username == username)
    if not user:
        raise HTTPException(status_code=404, detail="Usuari no trobat")
    nou_username = req.username.strip() if req.username else None
    if nou_username and nou_username != username:
        if User.get_or_none(User.username == nou_username):
            raise HTTPException(status_code=400, detail="El nou nom d'usuari ja existeix")
        user.username = nou_username
    if req.password:
        user.hashed_password = get_password_hash(req.password)
    if req.role:
        if user.role == "admin" and req.role != "admin" and comptar_admins() <= 1:
            raise HTTPException(status_code=400, detail="Ha de quedar com a mínim un administrador")
        user.role = req.role
    user.save()
    return {"estat": "ok", "missatge": "Usuari actualitzat"}


@app.delete("/api/admin/users/{username}")
def eliminar_usuari(username: str, admin: User = Depends(verificar_admin)):
    if username == admin.username:
        raise HTTPException(status_code=400, detail="No et pots eliminar a tu mateix")
    user = User.get_or_none(User.username == username)
    if not user:
        raise HTTPException(status_code=404, detail="Usuari no trobat")
    user.delete_instance()
    return {"estat": "ok", "missatge": "Usuari eliminat"}


# ---------- Fitxers ----------
# Llistar, esborrar, canviar el nom i descarregar és només per a
# administradors (el gestor de fitxers de l'app és d'admin). Crear carpetes
# i pujar fitxers és per a tothom, perquè ho fan servir les còpies de seguretat.
@app.get("/api/files/list")
def llistar_directori(path: str = "", admin: User = Depends(verificar_admin)):
    ruta_actual = obtenir_ruta_segura(path)
    if not ruta_actual.is_dir():
        raise HTTPException(status_code=404, detail="La carpeta no existeix")
    contingut = []
    for ruta_item in ruta_actual.iterdir():
        if ruta_item.name.startswith("."):
            continue
        stat = ruta_item.stat()
        es_carpeta = ruta_item.is_dir()
        contingut.append({
            "nom": ruta_item.name,
            "es_carpeta": es_carpeta,
            "mida_bytes": 0 if es_carpeta else stat.st_size,
            "mtime": stat.st_mtime,
            "ruta_completa": f"{path}/{ruta_item.name}".strip("/"),
        })
    contingut.sort(key=lambda x: (not x["es_carpeta"], x["nom"].lower()))
    return {"ruta_actual": path, "contingut": contingut}


@app.post("/api/files/mkdir")
def crear_carpeta(req: MkdirRequest = Body(...), user: User = Depends(obtenir_usuari_actual)):
    nom = validar_nom(req.nom_carpeta)
    ruta_base = req.path.strip("/")
    ruta_pare = obtenir_ruta_segura(ruta_base)
    if req.allow_reuse:
        nom_final = nom
    else:
        nom_final = obtenir_nom_unic(ruta_pare, nom)
    nova_ruta = ruta_pare / nom_final
    if nova_ruta.exists() and not nova_ruta.is_dir():
        raise HTTPException(status_code=400, detail="Ja existeix un fitxer amb aquest nom")
    nova_ruta.mkdir(parents=True, exist_ok=True)
    if req.mtime is not None:
        os.utime(nova_ruta, (req.mtime, req.mtime))
    return {
        "estat": "ok",
        "missatge": "Carpeta creada",
        "nom_final": nom_final,
        "ruta_completa": f"{ruta_base}/{nom_final}".strip("/"),
    }


@app.post("/api/files/upload")
def pujar_fitxer(
    path: str = Form(""),
    file: UploadFile = File(...),
    mtime: Optional[float] = Form(None),
    user: User = Depends(obtenir_usuari_actual),
):
    nom = validar_nom(Path(file.filename or "").name)
    ruta_directori = obtenir_ruta_segura(path)
    ruta_directori.mkdir(parents=True, exist_ok=True)
    nom_unic = obtenir_nom_unic(ruta_directori, nom)
    ruta_final = ruta_directori / nom_unic
    try:
        with ruta_final.open("wb") as buffer:
            shutil.copyfileobj(file.file, buffer)
    finally:
        file.file.close()
    if mtime is not None:
        os.utime(ruta_final, (mtime, mtime))
    return {"estat": "ok", "missatge": f"Fitxer pujat com a {nom_unic}"}


@app.put("/api/files/rename")
def canviar_nom(req: RenameRequest, admin: User = Depends(verificar_admin)):
    ruta_original = obtenir_ruta_segura(req.path)
    if ruta_original == NAS_STORAGE_PATH:
        raise HTTPException(status_code=403, detail="No pots canviar el nom de l'arrel")
    if not ruta_original.exists():
        raise HTTPException(status_code=404, detail="L'element original no existeix")
    nou_nom = validar_nom(req.nou_nom)
    ruta_nova = ruta_original.parent / nou_nom
    if ruta_nova.exists():
        raise HTTPException(status_code=400, detail="Ja existeix un element amb aquest nom")
    ruta_original.rename(ruta_nova)
    return {"estat": "ok", "missatge": f"Canviat de {ruta_original.name} a {nou_nom}"}


@app.delete("/api/files/delete")
def eliminar_element(path: str = Query(..., min_length=1), admin: User = Depends(verificar_admin)):
    ruta_objectiu = obtenir_ruta_segura(path)
    if ruta_objectiu == NAS_STORAGE_PATH:
        raise HTTPException(status_code=403, detail="No pots esborrar l'arrel")
    if not ruta_objectiu.exists():
        raise HTTPException(status_code=404, detail="L'element no existeix")
    if ruta_objectiu.is_dir():
        shutil.rmtree(ruta_objectiu)
    else:
        ruta_objectiu.unlink()
    return {"estat": "ok", "missatge": "Element eliminat"}


@app.get("/api/files/download")
def descarregar_fitxer(request: Request, path: str = Query(...), token: Optional[str] = Query(None)):
    # S'accepta el token a la capçalera Authorization (recomanat) o, per
    # compatibilitat, com a paràmetre ?token=.
    capcalera = request.headers.get("Authorization", "")
    token_final = capcalera.removeprefix("Bearer ").strip() if capcalera.startswith("Bearer ") else token
    if not token_final:
        raise HTTPException(status_code=401, detail="Cal autenticació")
    user = usuari_des_de_token(token_final)
    if user.role != "admin":
        raise HTTPException(status_code=403, detail="Es requereixen permisos d'administrador")

    ruta_final = obtenir_ruta_segura(path)
    if not ruta_final.exists():
        raise HTTPException(status_code=404, detail="El fitxer no existeix")
    if ruta_final.is_dir():
        tmp_dir = tempfile.mkdtemp(prefix="nas_zip_")
        zip_path = shutil.make_archive(os.path.join(tmp_dir, ruta_final.name or "nas"), "zip", ruta_final)
        return FileResponse(
            path=zip_path,
            filename=f"{ruta_final.name}.zip",
            media_type="application/zip",
            background=BackgroundTask(esborrar_silenciosament, tmp_dir),
        )
    return FileResponse(path=ruta_final, filename=ruta_final.name, media_type="application/octet-stream")


# ---------- Estat del sistema ----------
@app.get("/api/system/ping")
def ping(user: User = Depends(obtenir_usuari_actual)):
    return {"estat": "pong", "timestamp": datetime.now(timezone.utc).isoformat()}


@app.get("/api/system/status")
def estat_resumit(user: User = Depends(obtenir_usuari_actual)):
    return {
        **info_disc(),
        "cpu_percent": psutil.cpu_percent(interval=None),
        "ram_percent": psutil.virtual_memory().percent,
    }


@app.get("/api/system/details")
def estat_detallat(user: User = Depends(obtenir_usuari_actual)):
    vm = psutil.virtual_memory()
    sm = psutil.swap_memory()
    cpu_freq = psutil.cpu_freq()
    temperatures = []
    try:
        for name, entries in psutil.sensors_temperatures().items():
            for entry in entries:
                temperatures.append({
                    "sensor": name,
                    "label": entry.label or name,
                    "current": entry.current,
                    "high": entry.high,
                    "critical": entry.critical,
                })
    except (AttributeError, OSError):
        pass  # Sense sensors disponibles (p. ex. en una màquina virtual)
    gb = 1024 ** 3
    return {
        "timestamp": datetime.now(timezone.utc).isoformat(),
        "disk": info_disc(),
        "cpu": {
            "percent_total": psutil.cpu_percent(interval=None),
            "percent_per_core": psutil.cpu_percent(interval=None, percpu=True),
            "count_logical": psutil.cpu_count(),
            "count_physical": psutil.cpu_count(logical=False),
            "freq_current_mhz": round(cpu_freq.current, 1) if cpu_freq else None,
            "freq_min_mhz": round(cpu_freq.min, 1) if cpu_freq else None,
            "freq_max_mhz": round(cpu_freq.max, 1) if cpu_freq else None,
        },
        "ram": {
            "total_gb": round(vm.total / gb, 2),
            "available_gb": round(vm.available / gb, 2),
            "used_gb": round(vm.used / gb, 2),
            "percent": vm.percent,
        },
        "swap": {
            "total_gb": round(sm.total / gb, 2),
            "used_gb": round(sm.used / gb, 2),
            "free_gb": round(sm.free / gb, 2),
            "percent": sm.percent,
        },
        "temperatures": temperatures,
    }


# ---------- Test de rendiment ----------
@app.get("/api/system/test-download-stream")
async def test_download_stream(user: User = Depends(obtenir_usuari_actual)):
    async def generar_dades():
        chunk = b"0" * 1024 * 64
        for _ in range(800):  # ~50 MB
            yield chunk
            await asyncio.sleep(0.01)
    return StreamingResponse(generar_dades(), media_type="application/octet-stream")


@app.post("/api/system/test-upload")
async def test_upload(file: UploadFile = File(...), user: User = Depends(obtenir_usuari_actual)):
    # Es llegeix per blocs per no carregar tot el fitxer a memòria.
    mida = 0
    while chunk := await file.read(1024 * 1024):
        mida += len(chunk)
    return {"estat": "ok", "missatge": "Test completat", "mida": mida}


# ---------- Registre de l'app ----------
@app.post("/api/system/logs")
def rebre_logs(logs: List[LogEntry], user: User = Depends(obtenir_usuari_actual)):
    APP_LOGS_FILE.parent.mkdir(parents=True, exist_ok=True)
    with open(APP_LOGS_FILE, "a", encoding="utf-8") as f:
        for entry in logs:
            f.write(json.dumps(entry.model_dump(), ensure_ascii=False) + "\n")
    return {"estat": "ok", "missatge": f"{len(logs)} entrades rebudes"}


@app.get("/api/system/logs")
def llistar_logs(admin: User = Depends(verificar_admin)):
    if not APP_LOGS_FILE.exists():
        return []
    with open(APP_LOGS_FILE, "r", encoding="utf-8") as f:
        darreres = deque(f, maxlen=100)
    return [json.loads(linia) for linia in reversed(darreres) if linia.strip()]


# --------------------------------------------------------------------------
# Arrencada
# --------------------------------------------------------------------------
def generar_certificat_autosignat(cert_path: Path, key_path: Path) -> None:
    from cryptography import x509
    from cryptography.hazmat.primitives import hashes, serialization
    from cryptography.hazmat.primitives.asymmetric import rsa
    from cryptography.x509.oid import NameOID

    print("Generant certificat TLS autosignat...")
    key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    nom = x509.Name([
        x509.NameAttribute(NameOID.COUNTRY_NAME, "ES"),
        x509.NameAttribute(NameOID.ORGANIZATION_NAME, "NAS Private"),
        x509.NameAttribute(NameOID.COMMON_NAME, "nas-local.private"),
    ])
    ara = datetime.now(timezone.utc)
    cert = (
        x509.CertificateBuilder()
        .subject_name(nom)
        .issuer_name(nom)
        .public_key(key.public_key())
        .serial_number(x509.random_serial_number())
        .not_valid_before(ara)
        .not_valid_after(ara + timedelta(days=3650))
        .add_extension(x509.SubjectAlternativeName([x509.DNSName("localhost")]), critical=False)
        .sign(key, hashes.SHA256())
    )
    key_path.write_bytes(key.private_bytes(
        encoding=serialization.Encoding.PEM,
        format=serialization.PrivateFormat.TraditionalOpenSSL,
        encryption_algorithm=serialization.NoEncryption(),
    ))
    os.chmod(key_path, 0o600)
    cert_path.write_bytes(cert.public_bytes(serialization.Encoding.PEM))


if __name__ == "__main__":
    import uvicorn

    cert_path = BASE_DIR / os.getenv("SSL_CERT_FILE", "cert.pem")
    key_path = BASE_DIR / os.getenv("SSL_KEY_FILE", "key.pem")
    if not cert_path.exists() or not key_path.exists():
        generar_certificat_autosignat(cert_path, key_path)

    host = os.getenv("NAS_HOST", "0.0.0.0")
    port = int(os.getenv("NAS_PORT", "8000"))
    print(f"Iniciant servidor HTTPS a {host}:{port}")
    uvicorn.run(app, host=host, port=port, ssl_keyfile=str(key_path), ssl_certfile=str(cert_path))
