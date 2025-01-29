package l2ft.gameserver.network.l2.s2c;

public class ExEventMatchList extends L2GameServerPacket
{
	@Override
	protected void writeImpl()
	{
		writeEx(0x0D);
		// TODO пока не реализован даже в коиенте
	}
}