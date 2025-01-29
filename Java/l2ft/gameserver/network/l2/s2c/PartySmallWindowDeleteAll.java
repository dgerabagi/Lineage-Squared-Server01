package l2ft.gameserver.network.l2.s2c;

public class PartySmallWindowDeleteAll extends L2GameServerPacket
{
	public static final L2GameServerPacket STATIC = new PartySmallWindowDeleteAll();

	@Override
	protected final void writeImpl()
	{
		writeC(0x50);
	}
}