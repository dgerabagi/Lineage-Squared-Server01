package l2ft.gameserver.listener.zone;

import l2ft.commons.listener.Listener;
import l2ft.gameserver.model.Creature;
import l2ft.gameserver.model.Zone;

public interface OnZoneEnterLeaveListener extends Listener<Zone>
{
	public void onZoneEnter(Zone zone, Creature actor);

	public void onZoneLeave(Zone zone, Creature actor);
}
