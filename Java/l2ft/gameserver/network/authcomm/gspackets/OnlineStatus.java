package l2ft.gameserver.network.authcomm.gspackets;

import l2ft.gameserver.network.authcomm.SendablePacket;

public class OnlineStatus extends SendablePacket
{
	private boolean _online;

	public OnlineStatus(boolean online)
	{
		_online = online;
	}

	protected void writeImpl()
	{
		writeC(0x01);
		writeC(_online ? 1 : 0);
	}
}
