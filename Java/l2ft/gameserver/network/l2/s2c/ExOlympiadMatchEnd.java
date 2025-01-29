package l2ft.gameserver.network.l2.s2c;

public class ExOlympiadMatchEnd extends L2GameServerPacket
{
	public static final L2GameServerPacket STATIC = new ExOlympiadMatchEnd();

	@Override
	protected void writeImpl()
	{
		writeEx(0x2D);
	}
}