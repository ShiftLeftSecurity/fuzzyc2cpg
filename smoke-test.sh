#!/usr/bin/env bash

SCRIPT_ABS_PATH=$(readlink -f "$0")
SCRIPT_ABS_DIR=$(dirname "$SCRIPT_ABS_PATH")

# Setup
FUZZYC_TEST_DIR="$SCRIPT_ABS_DIR"/smoke-test-repos
FUZZYC_PARSER="$SCRIPT_ABS_DIR"/fuzzyc2cpg.sh

FUZZYC_TEST_PROJECTS=(
  "https://github.com/sobotka/blender.git;blender"
  "https://github.com/electron/electron.git;electron"
  "https://github.com/FFmpeg/FFmpeg.git;FFmpeg"
  "https://github.com/git/git.git;git"
  # TODO: OpenCV fails due to StackOverflowError in Antlr.
  # "https://github.com/opencv/opencv.git;opencv"
  "https://github.com/antirez/redis.git;redis"
  "https://github.com/tensorflow/tensorflow.git;tensorflow"
  "https://github.com/microsoft/terminal.git;terminal"
)

if ! type "git" > /dev/null; then
  echo "Please ensure Git is installed."
  exit 1
fi

mkdir -p "$FUZZYC_TEST_DIR"
cd "$FUZZYC_TEST_DIR" || exit 0

# Run parser over each test project.
for FUZZYC_PROJECT_TUPLE in "${FUZZYC_TEST_PROJECTS[@]}"
do
  # Extract git ref and project name.
  FUZZYC_TEST_GIT_REF=$(echo "$FUZZYC_PROJECT_TUPLE" | cut -d ";" -f 1)
  FUZZYC_TEST_PROJECT=$(echo "$FUZZYC_PROJECT_TUPLE" | cut -d ";" -f 2)

  # Clone project & run parser.
  echo "Testing project [$FUZZYC_TEST_PROJECT]"
  [ ! -d "$FUZZYC_TEST_PROJECT" ] && git clone --quiet "$FUZZYC_TEST_GIT_REF"
  "$FUZZYC_PARSER" \
    -J-Xmx4G \
    --source-file-ext=".cc,.hh" \
    --out="$FUZZYC_TEST_DIR/$FUZZYC_TEST_PROJECT.bin.zip" \
    "$FUZZYC_TEST_DIR/$FUZZYC_TEST_PROJECT"

  # Check status of parse
  TEST_EXIT_CODE=$?
  if [ $TEST_EXIT_CODE = 0 ]; then
    echo "Test for project [$FUZZYC_TEST_PROJECT] passed."
  else
    echo "Test for project [$FUZZYC_TEST_PROJECT] failed with exit code [$TEST_EXIT_CODE]."
    break
  fi
done

# Cleanup
cd - || exit 0
rm -rf "$FUZZYC_TEST_DIR"
