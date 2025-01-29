package l2ft.gameserver.network.l2.s2c;

public class ExShowPetitionHtml extends L2GameServerPacket
{
	@Override
	protected void writeImpl()
	{
		writeEx(0xB1);
		// TODO dx[dcS]
	}
}