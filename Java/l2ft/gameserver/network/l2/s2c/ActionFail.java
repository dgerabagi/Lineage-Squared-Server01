package l2ft.gameserver.network.l2.s2c;

public class ActionFail extends L2GameServerPacket
{
	public static final L2GameServerPacket STATIC = new ActionFail();

	@Override
	protected final void writeImpl()
	{
		writeC(0x1f);
	}
}