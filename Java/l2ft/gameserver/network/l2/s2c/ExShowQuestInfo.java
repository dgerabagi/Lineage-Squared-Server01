package l2ft.gameserver.network.l2.s2c;

public class ExShowQuestInfo extends L2GameServerPacket
{
	public static final L2GameServerPacket STATIC = new ExShowQuestInfo();

	@Override
	protected final void writeImpl()
	{
		writeEx(0x20);
	}
}