package l2ft.gameserver.listener.actor.npc;

import l2ft.gameserver.listener.NpcListener;
import l2ft.gameserver.model.instances.NpcInstance;

public interface OnSpawnListener extends NpcListener
{
	public void onSpawn(NpcInstance actor);
}
