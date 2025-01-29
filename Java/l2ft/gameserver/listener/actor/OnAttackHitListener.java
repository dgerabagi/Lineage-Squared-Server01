package l2ft.gameserver.listener.actor;

import l2ft.gameserver.listener.CharListener;
import l2ft.gameserver.model.Creature;

public interface OnAttackHitListener extends CharListener
{
	public void onAttackHit(Creature actor, Creature attacker);
}
