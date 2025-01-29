package l2ft.gameserver.listener.actor.npc;

import l2ft.gameserver.listener.NpcListener;
import l2ft.gameserver.model.instances.NpcInstance;

public interface OnDecayListener extends NpcListener
{
	public void onDecay(NpcInstance actor);
}
