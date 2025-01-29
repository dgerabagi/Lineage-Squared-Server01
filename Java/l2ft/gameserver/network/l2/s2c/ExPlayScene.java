package l2ft.gameserver.network.l2.s2c;

/**
 * Format: ch
 * Протокол 828: при отправке пакета клиенту ничего не происходит.
 */
public class ExPlayScene extends L2GameServerPacket
{
	@Override
	protected void writeImpl()
	{
		writeEx(0x5c);
		writeD(0x00); //Kamael
	}
}