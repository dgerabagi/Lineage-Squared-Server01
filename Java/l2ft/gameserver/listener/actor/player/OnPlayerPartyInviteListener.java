package l2ft.gameserver.listener.actor.player;

import l2ft.gameserver.listener.PlayerListener;
import l2ft.gameserver.model.Player;

public interface OnPlayerPartyInviteListener extends PlayerListener
{
	public void onPartyInvite(Player player);
}
