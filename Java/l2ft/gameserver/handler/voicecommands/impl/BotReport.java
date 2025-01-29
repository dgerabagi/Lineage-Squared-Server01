package l2ft.gameserver.handler.voicecommands.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import l2ft.gameserver.handler.voicecommands.IVoicedCommandHandler;
import l2ft.gameserver.model.GameObjectsStorage;
import l2ft.gameserver.model.Player;
import l2ft.gameserver.model.Zone.ZoneType;
import l2ft.gameserver.scripts.Functions;

public class BotReport extends Functions implements IVoicedCommandHandler
{
	private static Map<Player, Integer> _reports = new HashMap<>();
	private static Map<Player, List<Player>> _playerReports = new HashMap<>();
	@Override
	public boolean useVoicedCommand(String command, Player activeChar, String target)
	{
		Player playerTarget = null;//activeChar.getTarget();
		if(activeChar.getTarget() == null || !activeChar.getTarget().isPlayer() || activeChar.getPlayer() == playerTarget)
		{
			activeChar.sendMessage("You can use this command, only while targetting other player");
			return false;
		}
		playerTarget = activeChar.getTarget().getPlayer();
		if(_playerReports.containsKey(activeChar))
			if(_playerReports.get(activeChar).contains(playerTarget))
			{
				activeChar.sendMessage("You cannot report this player so often!");
				return false;
			}
		if(playerTarget.isInZone(ZoneType.peace_zone) || playerTarget.isDead())
		{
			activeChar.sendMessage("You cannot report this player!");
			return false;
		}
		if(_playerReports.get(activeChar) == null)
		{
			List<Player> list = new ArrayList<>();
			_playerReports.put(activeChar, list);
		}
		_playerReports.get(activeChar).add(playerTarget);
		if(!_reports.containsKey(playerTarget))
			_reports.put(playerTarget, 0);
		_reports.put(playerTarget, _reports.get(playerTarget)+1);
		
		for(Player gm : GameObjectsStorage.getAllPlayersForIterate())
			if(gm.isGM())
				gm.sendMessage(playerTarget.getName()+" has been reported as a bot!");
		return true;
	}

	@Override
	public String[] getVoicedCommandList() 
	{
		return new String[] {"report"};
	}
	
	public static Map<Player, Integer> getReports()
	{
		return _reports;
	}
	
	public static void clearPlayerReports(Player player)
	{
		_reports.remove(player);
	}
}
