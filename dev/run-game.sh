#!/bin/sh
# Runs one game server on the host and starts it again whenever it stops by itself, which it does after every round.
# Ctrl-C, or a Gradle run that fails, ends the loop.
set -u
game=${1:?usage: dev/run-game.sh <game> [gradle arguments]}
shift
cd "$(dirname "$0")/.." || exit 1
while ./gradlew :paper:runServer -Pgame="$game" "$@"; do
    echo "$game stopped, starting it again"
done
