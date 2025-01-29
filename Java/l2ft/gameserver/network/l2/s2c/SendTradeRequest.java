package l2ft.gameserver.network.l2.s2c;

public class SendTradeRequest extends L2GameServerPacket
{
	private int _senderId;

	public SendTradeRequest(int senderId)
	{
		_senderId = senderId;
	}

	@Override
	protected final void writeImpl()
	{
		writeC(0x70);
		writeD(_senderId);
	}
}