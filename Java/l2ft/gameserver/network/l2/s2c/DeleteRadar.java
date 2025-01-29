package l2ft.gameserver.network.l2.s2c;

public class DeleteRadar extends L2GameServerPacket
{
	@Override
	protected final void writeImpl()
	{
		writeC(0xB8);
		//TODO ddd
	}
}