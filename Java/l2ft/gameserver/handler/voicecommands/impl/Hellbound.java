package l2ft.gameserver.handler.voicecommands.impl;

import l2ft.gameserver.handler.voicecommands.IVoicedCommandHandler;
import l2ft.gameserver.instancemanager.HellboundManager;
import l2ft.gameserver.model.Player;
import l2ft.gameserver.scripts.Functions;

public class Hellbound extends Functions implements IVoicedCommandHandler
{
	private final String[] _commandList = new String[] { "hellbound" };

	@Override
	public String[] getVoicedCommandList()
	{
		return _commandList;
	}

	@Override
	public boolean useVoicedCommand(String command, Player activeChar, String target)
	{
		if(command.equals("hellbound"))
		{
			activeChar.sendMessage("Hellbound level: " + HellboundManager.getHellboundLevel());
			activeChar.sendMessage("Confidence: " + HellboundManager.getConfidence());
		}
		return false;
	}
}
