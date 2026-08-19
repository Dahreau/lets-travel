#!/bin/sh
set -e

dir="$(dirname "$0")"

"$dir/start-ci.sh"
"$dir/start-app.sh"
