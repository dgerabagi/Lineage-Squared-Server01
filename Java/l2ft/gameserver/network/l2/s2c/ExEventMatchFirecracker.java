package l2ft.gameserver.network.l2.s2c;

public class ExEventMatchFirecracker extends L2GameServerPacket
{
	@Override
	protected void writeImpl()
	{
		writeEx(0x05);
		// TODO d
	}
}