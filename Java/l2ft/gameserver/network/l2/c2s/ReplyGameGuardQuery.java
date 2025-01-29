package l2ft.gameserver.network.l2.c2s;

import l2ft.gameserver.network.l2.GameClient;

public class ReplyGameGuardQuery extends L2GameClientPacket
{
	// Format: cdddd

	public byte[] _data = new byte[72];

	@Override
	protected void readImpl()
	{
		l2ft.gameserver.ccpGuard.Protection.doReadReplyGameGuard(getClient(), _buf, _data);
	}

	@Override
	protected void runImpl()
	{
		l2ft.gameserver.ccpGuard.Protection.doReplyGameGuard(getClient(), _data);
	}
}