package l2ft.gameserver.network.l2.s2c;

public class CharacterCreateSuccess extends L2GameServerPacket
{
	public static final L2GameServerPacket STATIC = new CharacterCreateSuccess();

	@Override
	protected final void writeImpl()
	{
		writeC(0x0f);
		writeD(0x01);
	}
}