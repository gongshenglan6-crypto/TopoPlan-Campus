@echo off
setlocal
title TopoPlan
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0start-topoplan.ps1"
if errorlevel 1 (
 echo Startup failed. See logs folder.
 pause
)
endlocal
