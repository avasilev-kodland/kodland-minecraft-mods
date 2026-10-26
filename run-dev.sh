#!/usr/bin/env bash
# Starts the Fabric dev client for one mod: ./run-dev.sh camera
# No launcher and no Microsoft account needed. Uses ../.tools/jdk-25 when it
# exists next to this folder, otherwise whatever JAVA_HOME already points at.
set -euo pipefail
cd "$(dirname "$0")"

MOD="${1:-}"
if [[ -z "$MOD" || ! -f "$MOD/build.gradle" ]]; then
	echo "usage: ./run-dev.sh <mod>   (mods: $(for d in */; do [[ -f "$d/build.gradle" ]] && printf '%s ' "${d%/}"; done))"
	exit 1
fi
shift

LOCAL_JDK="../.tools/jdk-25/Contents/Home"
if [[ -x "$LOCAL_JDK/bin/java" ]]; then
	export JAVA_HOME="$(cd "$LOCAL_JDK" && pwd)"
fi
echo "Java: ${JAVA_HOME:-system}"

./gradlew ":$MOD:runClient" "$@"
