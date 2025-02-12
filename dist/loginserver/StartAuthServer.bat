@echo off
title First-Team: Login Server Console
:start
echo Starting LoginServer.
echo.
java -server -Dfile.encoding=UTF-8 -Xms64m -Xmx128m -Xbootclasspath/p:../libs/l2ft.jar -cp config/xml;../libs/*; l2ft.loginserver.AuthServer
if ERRORLEVEL 2 goto restart
if ERRORLEVEL 1 goto error
goto end
:restart
echo.
echo Server restarted ...
echo.
goto start
:error
echo.
echo Server terminated abnormaly ...
echo.
:end
echo.
echo Server terminated ...
echo.

pause
