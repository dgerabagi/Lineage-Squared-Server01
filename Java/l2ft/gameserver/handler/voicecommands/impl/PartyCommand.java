package l2ft.gameserver.handler.voicecommands.impl;

import l2ft.gameserver.handler.bbs.CommunityBoardManager;
import l2ft.gameserver.handler.bbs.ICommunityBoardHandler;
import l2ft.gameserver.handler.voicecommands.IVoicedCommandHandler;
import l2ft.gameserver.model.Player;

public class PartyCommand implements IVoicedCommandHandler
{
	private static final String[] COMMANDS = {"lfp"};

	@Override
	public boolean useVoicedCommand(String command, Player activeChar, String target)
	{
		if(command.equals("lfp"))
		{
			ICommunityBoardHandler handler = CommunityBoardManager.getInstance().getCommunityHandler("_bbslink");
			if(handler != null)
			{
				handler.onBypassCommand(activeChar, "_bbslink");
			}
		}
		return true;
	}
	
	@Override
	public String[] getVoicedCommandList() 
	{
		return COMMANDS;
	}

}