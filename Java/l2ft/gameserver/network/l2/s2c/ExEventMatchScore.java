package l2ft.gameserver.network.l2.s2c;

public class ExEventMatchScore extends L2GameServerPacket
{
	@Override
	protected void writeImpl()
	{
		writeEx(0x10);
		// TODO ddd
	}
}