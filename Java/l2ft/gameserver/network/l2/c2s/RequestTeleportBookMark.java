package l2ft.gameserver.network.l2.c2s;

import l2ft.gameserver.model.Player;

public class RequestTeleportBookMark extends L2GameClientPacket
{
	private int slot;

	@Override
	protected void readImpl()
	{
		slot = readD();
	}

	@Override
	protected void runImpl()
	{
		Player activeChar = getClient().getActiveChar();
		if(activeChar != null)
			activeChar.bookmarks.tryTeleport(slot);
	}
}