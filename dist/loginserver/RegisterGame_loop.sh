#!/bin/bash

while :;
do
	java -server -Xmx16m -Xbootclasspath/p:../serverslibs/l2ft.jar -cp config/xml;../serverslibs/*; l2ft.loginserver.GameServerRegister

	[ $? -ne 2 ] && break
	sleep 10;
done
