#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

APK_PATH="app/build/outputs/apk/debug/app-debug.apk"

if ! command -v adb >/dev/null 2>&1; then
  echo "Error: adb no esta instalado o no esta en PATH." >&2
  exit 1
fi

echo "==> Verificando dispositivo ADB"
if [[ "$(adb get-state 2>/dev/null || true)" != "device" ]]; then
  echo "Error: no hay dispositivo/emulador ADB en estado 'device'." >&2
  echo "Tip: ejecuta 'adb devices' y conecta/desbloquea el telefono." >&2
  exit 1
fi

echo "==> Compilando APK debug"
./gradlew :app:assembleDebug

if [[ ! -f "$APK_PATH" ]]; then
  echo "Error: no se encontro APK en $APK_PATH" >&2
  exit 1
fi

echo "==> Instalando APK por ADB"
adb install -r "$APK_PATH"

echo "==> Listo: build + instalacion completados"
