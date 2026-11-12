@echo off
setlocal EnableExtensions

set "ROOT_DIR=%~dp0"
set "BACKEND_DIR=%ROOT_DIR%backend\demo1"


rem Runtime secrets must be supplied by the local .env or process environment.

pushd "%BACKEND_DIR%"
call mvn spring-boot:run
set "MAVEN_EXIT_CODE=%ERRORLEVEL%"
popd

if not "%MAVEN_EXIT_CODE%" == "0" (
    echo.
    echo [ERROR] Spring Boot backend stopped with exit code %MAVEN_EXIT_CODE%.
)

endlocal & exit /b %MAVEN_EXIT_CODE%
