#!/bin/bash

while :;
do
	java -server -Dfile.encoding=UTF-8 -Xmx1G -Xbootclasspath/p:../serverslibs/l2ft.jar -cp config/xml:../serverslibs/*: com.lameguard.LameGuard l2ft.gameserver.GameServer > log/stdout.log 2>&1

	[ $? -ne 2 ] && break
	sleep 30;
done

