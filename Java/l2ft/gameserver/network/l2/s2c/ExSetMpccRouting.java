package l2ft.gameserver.network.l2.s2c;

public class ExSetMpccRouting extends L2GameServerPacket
{
	@Override
	protected void writeImpl()
	{
		writeEx(0x37);
		// TODO d
	}
}