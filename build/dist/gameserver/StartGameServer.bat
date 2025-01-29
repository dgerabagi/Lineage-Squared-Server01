@echo off
title First-Team: Game Server Console
:start
echo Starting GameServer.
echo.

java -server -Dfile.encoding=UTF-8 -mx4g -cp config/xml;../libs/*; l2ft.gameserver.GameServer

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
