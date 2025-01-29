package l2ft.gameserver.handler.voicecommands.impl;

import l2ft.gameserver.Config;
import l2ft.gameserver.handler.voicecommands.IVoicedCommandHandler;
import l2ft.gameserver.model.Player;
import l2ft.gameserver.network.l2.components.CustomMessage;

public class Debug implements IVoicedCommandHandler
{
	private final String[] _commandList = new String[] { "debug" };

	@Override
	public String[] getVoicedCommandList()
	{
		return _commandList;
	}

	@Override
	public boolean useVoicedCommand(String command, Player player, String args)
	{
		if (!Config.ALT_DEBUG_ENABLED)
			return false;

		if (player.isDebug())
		{
			player.setDebug(false);
			player.sendMessage(new CustomMessage("voicedcommandhandlers.Debug.Disabled", player));
		}
		else
		{
			player.setDebug(true);
			player.sendMessage(new CustomMessage("voicedcommandhandlers.Debug.Enabled", player));
		}
		return true;
	}
}
