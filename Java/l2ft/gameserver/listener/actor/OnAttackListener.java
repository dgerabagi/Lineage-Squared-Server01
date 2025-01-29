package l2ft.gameserver.listener.actor;

import l2ft.gameserver.listener.CharListener;
import l2ft.gameserver.model.Creature;

public interface OnAttackListener extends CharListener
{
	public void onAttack(Creature actor, Creature target);
}
