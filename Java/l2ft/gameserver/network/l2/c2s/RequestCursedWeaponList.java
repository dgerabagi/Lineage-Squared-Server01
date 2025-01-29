package l2ft.gameserver.network.l2.c2s;

import l2ft.gameserver.model.Creature;
import l2ft.gameserver.network.l2.s2c.ExCursedWeaponList;

public class RequestCursedWeaponList extends L2GameClientPacket
{
	@Override
	protected void readImpl()
	{}

	@Override
	protected void runImpl()
	{
		Creature activeChar = getClient().getActiveChar();
		if(activeChar == null)
			return;

		activeChar.sendPacket(new ExCursedWeaponList());
	}
}