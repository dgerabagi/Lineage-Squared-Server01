Client Protocol 273

Login Server port 2106

Put library and lameguard folder into gameserver folder

Change GS start script: add lameguard library first in classpath, and before server main class write "com.lameguard.LameGuard"

Example:

java ...(parameters) -cp lameguard-1.9.5.jar:...(libraries) com.lameguard.LameGuard com.l2jserver...(main gameserver class)

- admin should never put a name of antibot on his site or forum, never give a link to lameguard.com or upload server files anywhere except own server

- client and server modules should be updated atleast ONCE in 6 months, update cost is listed on site

- notify about new version shoud be seen in gameserver console, your version number is in "version" file