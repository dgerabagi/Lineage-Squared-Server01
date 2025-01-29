package l2ft.gameserver.handler.bbs;

import l2ft.gameserver.model.Player;

public interface ICommunityBoardHandler
{
	public String[] getBypassCommands();

	public void onBypassCommand(Player player, String bypass);

	public void onWriteCommand(Player player, String bypass, String arg1, String arg2, String arg3, String arg4, String arg5);
}
