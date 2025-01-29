package l2ft.gameserver.network.l2.s2c;

public class AttackinCoolTime extends L2GameServerPacket
{
	@Override
	protected void writeImpl()
	{
		// just trigger - без аргументов
		writeC(0x03);
	}
}