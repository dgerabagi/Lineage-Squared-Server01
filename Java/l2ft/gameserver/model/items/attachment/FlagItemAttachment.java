package l2ft.gameserver.model.items.attachment;

import l2ft.gameserver.model.Creature;
import l2ft.gameserver.model.Player;
import l2ft.gameserver.model.Skill;

/**
 * @author VISTALL
 * @date 15:49/26.03.2011
 */
public interface FlagItemAttachment extends PickableAttachment
{
	//FIXME [VISTALL] возможно переделать на слушатели игрока
	void onLogout(Player player);
	//FIXME [VISTALL] возможно переделать на слушатели игрока
	void onDeath(Player owner, Creature killer);

	boolean canAttack(Player player);

	boolean canCast(Player player, Skill skill);
}
