package l2ft.loginserver.network.gameservercon.lspackets;

import l2ft.loginserver.network.gameservercon.SendablePacket;

public class PingRequest extends SendablePacket
{
	@Override
	protected void writeImpl()
	{
		writeC(0xff);
	}
}