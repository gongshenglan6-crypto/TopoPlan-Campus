@echo off
setlocal EnableExtensions

set "FRONTEND_DIR=%~dp0frontend"

pushd "%FRONTEND_DIR%"
call npm run dev -- --host 0.0.0.0 --port 5173 --strictPort
set "NPM_EXIT_CODE=%ERRORLEVEL%"
popd

if not "%NPM_EXIT_CODE%" == "0" (
    echo.
    echo [ERROR] Vue frontend stopped with exit code %NPM_EXIT_CODE%.
)

endlocal & exit /b %NPM_EXIT_CODE%
