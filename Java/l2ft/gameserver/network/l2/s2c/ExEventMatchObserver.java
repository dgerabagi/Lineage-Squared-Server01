package l2ft.gameserver.network.l2.s2c;

public class ExEventMatchObserver extends L2GameServerPacket
{
	@Override
	protected void writeImpl()
	{
		writeEx(0x0E);
		// TODO dccSS
	}
}