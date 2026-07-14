@echo off
setlocal EnableExtensions EnableDelayedExpansion

set "POM_FILE=pom.xml"

if not exist "%POM_FILE%" (
  echo [ERROR] "%POM_FILE%" introuvable dans le dossier courant.
  exit /b 1
)

for /f "usebackq delims=" %%V in (`powershell -NoProfile -Command "[xml]$p=Get-Content '%POM_FILE%'; $p.project.version"`) do (
  set "VERSION=%%V"
)

if not defined VERSION (
  echo [ERROR] Impossible de lire ^<version^> dans "%POM_FILE%".
  exit /b 1
)

set "TAG=v%VERSION%"
echo [INFO] Version detectee: %VERSION%
echo [INFO] Tag cible: %TAG%

git rev-parse --git-dir >nul 2>&1
if errorlevel 1 (
  echo [ERROR] Ce dossier n'est pas un depot Git.
  exit /b 1
)

git tag -d "%TAG%" >nul 2>&1
if not errorlevel 1 (
  echo [INFO] Tag local existant supprime: %TAG%
)

git push origin ":refs/tags/%TAG%" >nul 2>&1
if not errorlevel 1 (
  echo [INFO] Tag distant existant supprime: %TAG%
)

git tag -a "%TAG%" -m "Release %TAG%"
if errorlevel 1 (
  echo [ERROR] Echec creation du tag "%TAG%".
  exit /b 1
)

git push origin "%TAG%"
if errorlevel 1 (
  echo [ERROR] Echec push du tag "%TAG%" vers origin.
  exit /b 1
)

echo [OK] Tag cree/mis a jour et pousse: %TAG%
endlocal
exit /b 0
