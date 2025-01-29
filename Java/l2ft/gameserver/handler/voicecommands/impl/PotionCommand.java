package l2ft.gameserver.handler.voicecommands.impl;

import l2ft.gameserver.handler.voicecommands.IVoicedCommandHandler;
import l2ft.gameserver.model.Player;
import l2ft.gameserver.taskmanager.AutoPotionsManager;

public class PotionCommand implements IVoicedCommandHandler
{
	private final String[] _commandList = new String[] {"autoPotion"};

	@Override
	public String[] getVoicedCommandList()
	{
		return _commandList;
	}

	@Override
	public boolean useVoicedCommand(String command, Player player, String args)
	{
		if(AutoPotionsManager.getInstance().playerUseAutoPotion(player))
			AutoPotionsManager.getInstance().removePlayer(player);
		else
			AutoPotionsManager.getInstance().addNewPlayer(player);

		return true;
	}
}
