package l2ft.gameserver.listener.actor;

import l2ft.gameserver.listener.CharListener;
import l2ft.gameserver.model.Creature;
import l2ft.gameserver.model.Skill;

public interface OnMagicHitListener extends CharListener
{
	public void onMagicHit(Creature actor, Skill skill, Creature caster);
}
