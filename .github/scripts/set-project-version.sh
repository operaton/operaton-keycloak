#!/usr/bin/env bash

if [ ! -f "mvnw" ]; then
  echo "⚠️ Maven Wrapper not found. You must execute this script from the project root directory. Exiting..."
  exit 1
fi

if [ -z "$1" ]; then
  echo "⚠️ You must provide the new version as an argument. Exiting..."
  exit 1
fi
NEW_VERSION=$1
CURRENT_VERSION=$(./mvnw help:evaluate -N -q -Dexpression=project.version -DforceStdout |tail -n 1)

echo "🔍 Current project version is $CURRENT_VERSION, updating to $NEW_VERSION"
echo "🔄 Updating version in pom.xml files"

./mvnw versions:set -q -DnewVersion=$NEW_VERSION


MISSED_FILES=$(grep -R "$CURRENT_VERSION" --include pom.xml .)
if [ -n "$MISSED_FILES" ]; then
  echo "⚠️ The following files still contain the old version:"
  echo "$MISSED_FILES"
fi

echo "✅ Version updated to $NEW_VERSION"
