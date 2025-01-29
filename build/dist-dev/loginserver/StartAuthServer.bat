@echo off
title First-Team: Login Server Console
:start
echo Starting LoginServer.
echo.

"C:\Program Files\Java\jdk1.7.0_80\bin\java.exe" -server -Dfile.encoding=UTF-8 -Xms64m -Xmx128m -cp .;../libs/l2ft.jar;config/xml;../libs/* l2ft.loginserver.AuthServer

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
