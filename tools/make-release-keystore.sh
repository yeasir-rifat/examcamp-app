#!/usr/bin/env bash
# Run this ONCE on your own computer (needs Java's `keytool`). Do not run it on a shared machine.
# It creates the release keystore, prints the 4 values for GitHub Secrets and the SHA-1 / SHA-256
# you must add to Firebase.
set -euo pipefail

OUT="${1:-examcamp-release.keystore}"
ALIAS="${KEY_ALIAS:-examcamp}"

if [ -e "$OUT" ]; then echo "$OUT already exists. Refusing to overwrite."; exit 1; fi

read -r -s -p "Choose a keystore password (min 8 chars): " PASS; echo
read -r -s -p "Repeat it: " PASS2; echo
[ "$PASS" = "$PASS2" ] || { echo "Passwords differ."; exit 1; }
[ "${#PASS}" -ge 8 ] || { echo "Too short."; exit 1; }

# PKCS12 uses one password for store and key, so KEYSTORE_PASSWORD == KEY_PASSWORD.
keytool -genkeypair -v \
  -keystore "$OUT" -storetype PKCS12 \
  -alias "$ALIAS" -keyalg RSA -keysize 2048 -validity 10000 \
  -storepass "$PASS" -keypass "$PASS" \
  -dname "CN=Examcamp, O=Examcamp, C=BD"

echo
echo "=============== GitHub -> Settings -> Secrets and variables -> Actions ==============="
echo "KEYSTORE_PASSWORD = (the password you typed)"
echo "KEY_PASSWORD      = (the same password)"
echo "KEY_ALIAS         = $ALIAS"
echo "KEYSTORE_BASE64   = (one long line, printed below)"
if base64 --help 2>&1 | grep -q -- '-w'; then base64 -w0 "$OUT"; else base64 "$OUT" | tr -d '\n'; fi
echo
echo
echo "=============== Add these to Firebase (Project settings -> your Android app) ==============="
keytool -list -v -keystore "$OUT" -alias "$ALIAS" -storepass "$PASS" | grep -E "SHA1|SHA256"
echo
echo "BACK UP $OUT somewhere safe (password manager / drive). If you lose it you can never"
echo "update the app with this key. Never commit it to git."
