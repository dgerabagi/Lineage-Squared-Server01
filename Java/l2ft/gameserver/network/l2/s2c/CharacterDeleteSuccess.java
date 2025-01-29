package l2ft.gameserver.network.l2.s2c;

public class CharacterDeleteSuccess extends L2GameServerPacket
{
	@Override
	protected final void writeImpl()
	{
		writeC(0x1d);
	}
}