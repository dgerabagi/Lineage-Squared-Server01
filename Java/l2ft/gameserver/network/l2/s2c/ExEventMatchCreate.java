package l2ft.gameserver.network.l2.s2c;

public class ExEventMatchCreate extends L2GameServerPacket
{
	@Override
	protected void writeImpl()
	{
		writeEx(0x1D);
		// TODO d
	}
}