package l2ft.gameserver.network.l2.c2s;

import l2ft.gameserver.model.Player;
import l2ft.gameserver.model.Zone;
import l2ft.gameserver.network.l2.components.SystemMsg;
import l2ft.gameserver.network.l2.s2c.ShowMiniMap;
import l2ft.gameserver.scripts.Functions;

public class RequestShowMiniMap extends L2GameClientPacket
{
	@Override
	protected void readImpl()
	{}

	@Override
	protected void runImpl()
	{
		Player activeChar = getClient().getActiveChar();
		if(activeChar == null)
			return;

		// Map of Hellbound
		if(activeChar.isActionBlocked(Zone.BLOCKED_ACTION_MINIMAP) ||
				(activeChar.isInZone("[Hellbound_territory]") && Functions.getItemCount(activeChar, 9994) == 0))
		{
			activeChar.sendPacket(SystemMsg.THIS_IS_AN_AREA_WHERE_YOU_CANNOT_USE_THE_MINI_MAP);
			return;
		}

		sendPacket(new ShowMiniMap(activeChar, 0));
	}
}