#!/usr/bin/env zsh

M2_REPO=~/.m2/repository
## Build the project first
## ./mvnw clean install
## Then run this script

function _main() {
  local jarPath="${M2_REPO}/com/kapresoft/devops/shell/0.0.1-SNAPSHOT/shell-0.0.1-SNAPSHOT.jar"
  echo "jarPath: ${jarPath}"
  if [[ ! -f "$jarPath" ]]; then
    echo "[ERROR]: Could not find spring shell jar: ${jarPath}."
    echo "Build the app first by running the following maven command:"
    echo "$ ./mvnw clean install"
    return 0
  fi
  local cmd
  cmd="java -jar ${jarPath}"
  echo "Executing: ${cmd}"
  eval "${cmd}"
}

_main
