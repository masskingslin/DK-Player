#!/usr/bin/env bash
# One-time helper: creates your own release keystore (works in Termux: pkg install openjdk-17).
# Usage: bash tools/make-keystore.sh
set -e
OUT="${1:-dk-release.jks}"
ALIAS="dkplayer"
read -rsp "Choose a keystore password (min 6 chars): " PASS; echo
keytool -genkeypair -v -keystore "$OUT" -alias "$ALIAS" -keyalg RSA -keysize 4096 \
  -validity 10000 -storepass "$PASS" -keypass "$PASS" -dname "CN=DK Player"
echo
echo "Add these 4 GitHub repository secrets (Settings > Secrets and variables > Actions):"
echo "  KEYSTORE_BASE64       = (contents printed below)"
echo "  DK_KEYSTORE_PASSWORD  = the password you just typed"
echo "  DK_KEY_ALIAS          = $ALIAS"
echo "  DK_KEY_PASSWORD       = the same password"
echo
echo "----- KEYSTORE_BASE64 -----"
base64 -w0 "$OUT" 2>/dev/null || base64 "$OUT" | tr -d '\n'
echo
echo "---------------------------"
echo "BACK UP $OUT somewhere safe. If you lose it you can never update the installed app."
