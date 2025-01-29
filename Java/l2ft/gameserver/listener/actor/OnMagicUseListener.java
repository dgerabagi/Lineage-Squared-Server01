package l2ft.gameserver.listener.actor;

import l2ft.gameserver.listener.CharListener;
import l2ft.gameserver.model.Creature;
import l2ft.gameserver.model.Skill;

public interface OnMagicUseListener extends CharListener
{
	public void onMagicUse(Creature actor, Skill skill, Creature target, boolean alt);
}
