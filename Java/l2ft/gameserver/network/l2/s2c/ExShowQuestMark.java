package l2ft.gameserver.network.l2.s2c;

public class ExShowQuestMark extends L2GameServerPacket
{
	private int _questId;

	public ExShowQuestMark(int questId)
	{
		_questId = questId;
	}

	@Override
	protected void writeImpl()
	{
		writeEx(0x21);
		writeD(_questId);
	}
}