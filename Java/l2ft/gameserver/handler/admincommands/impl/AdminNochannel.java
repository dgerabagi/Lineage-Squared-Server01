package l2ft.gameserver.handler.admincommands.impl;

import l2ft.gameserver.data.xml.holder.ItemHolder;
import l2ft.gameserver.handler.admincommands.IAdminCommandHandler;
import l2ft.gameserver.model.Player;
import l2ft.gameserver.templates.item.ItemTemplate;
import l2ft.gameserver.utils.AdminFunctions;
import l2ft.gameserver.utils.ItemFunctions;
import l2ft.gameserver.utils.Util;

public class AdminNochannel implements IAdminCommandHandler
{
	private static enum Commands
	{
	}

	@Override
	public boolean useAdminCommand(Enum comm, String[] wordList, String fullString, Player activeChar)
	{
		
		return true;
	}

	@Override
	public Enum[] getAdminCommandEnum()
	{
		return Commands.values();
	}
}