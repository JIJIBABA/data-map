#!/usr/bin/env bash
set -e

cd "$(dirname "$0")" || exit 1
./node_modules/.bin/vite
