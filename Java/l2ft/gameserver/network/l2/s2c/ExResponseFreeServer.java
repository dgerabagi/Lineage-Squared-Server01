package l2ft.gameserver.network.l2.s2c;

public class ExResponseFreeServer extends L2GameServerPacket
{
	@Override
	protected void writeImpl()
	{
		writeEx(0x77);
		// just trigger
	}
}