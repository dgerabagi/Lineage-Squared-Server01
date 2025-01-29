package l2ft.gameserver.ccpGuard.packets;

import l2ft.gameserver.network.l2.s2c.L2GameServerPacket;

public class L2ExtHost extends L2GameServerPacket
{

	private int host;
	private int port;

	public void L2ExtHost(int ip, int p)
	{
		this.host = ip;
		this.port = p;
	}

	@Override
	protected final void writeImpl()
	{
		writeC(0xb0);
		writeC(0xd0);
		writeD(host);
		writeD(port);

	}

	@Override
	public String getType()
	{
		return "[S] B0 L2ExtHost";
	}
}