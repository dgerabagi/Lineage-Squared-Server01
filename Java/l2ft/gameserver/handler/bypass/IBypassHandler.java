package l2ft.gameserver.handler.bypass;

import l2ft.gameserver.model.Player;
import l2ft.gameserver.model.instances.NpcInstance;

/**
 * @author VISTALL
 * @date 15:54/12.07.2011
 */
public interface IBypassHandler
{
	String[] getBypasses();

	void onBypassFeedback(NpcInstance npc, Player player, String command);
}
