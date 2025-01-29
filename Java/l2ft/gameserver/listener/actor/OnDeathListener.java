package l2ft.gameserver.listener.actor;

import l2ft.gameserver.listener.CharListener;
import l2ft.gameserver.model.Creature;

public interface OnDeathListener extends CharListener
{
	public void onDeath(Creature actor, Creature killer);
}
