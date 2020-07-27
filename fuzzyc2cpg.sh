#!/bin/sh

SCRIPT_ABS_PATH=$(readlink -f "$0")
SCRIPT_ABS_DIR=$(dirname $SCRIPT_ABS_PATH)

$SCRIPT_ABS_DIR/target/universal/stage/bin/fuzzyc2cpg -J-XX:+UseG1GC -J-XX:CompressedClassSpaceSize=128m -J-XX:+UseStringDeduplication -Dlogback.configurationFile=$SCRIPT_ABS_DIR/config/logback.xml $@
