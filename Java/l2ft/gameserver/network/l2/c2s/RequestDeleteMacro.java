package l2ft.gameserver.network.l2.c2s;

import l2ft.gameserver.model.Player;

public class RequestDeleteMacro extends L2GameClientPacket
{
	private int _id;

	/**
	 * format:		cd
	 */
	@Override
	protected void readImpl()
	{
		_id = readD();
	}

	@Override
	protected void runImpl()
	{
		Player activeChar = getClient().getActiveChar();
		if(activeChar == null)
			return;
		activeChar.deleteMacro(_id);
	}
}