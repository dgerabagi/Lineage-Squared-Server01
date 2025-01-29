package l2ft.gameserver.network.authcomm.gspackets;

import l2ft.gameserver.network.authcomm.SendablePacket;

public class PingResponse extends SendablePacket
{
	protected void writeImpl()
	{
		writeC(0xff);
		writeQ(System.currentTimeMillis());
	}
}