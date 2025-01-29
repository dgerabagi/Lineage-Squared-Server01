@echo off
title First-Team: Game Server Registration...
:start
echo Starting Game Server Registration.
echo.
java -server -Xms64m -Xmx64m -Xbootclasspath/p:../libs/l2ft.jar -cp config/xml;../libs/*; l2ft.loginserver.GameServerRegister

pause
