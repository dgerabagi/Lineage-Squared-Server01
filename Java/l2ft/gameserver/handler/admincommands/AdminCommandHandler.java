package l2ft.gameserver.handler.admincommands;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import l2ft.commons.data.xml.AbstractHolder;
import l2ft.gameserver.handler.admincommands.impl.AdminAdmin;
import l2ft.gameserver.handler.admincommands.impl.AdminAnnouncements;
import l2ft.gameserver.handler.admincommands.impl.AdminBan;
import l2ft.gameserver.handler.admincommands.impl.AdminCamera;
import l2ft.gameserver.handler.admincommands.impl.AdminCancel;
import l2ft.gameserver.handler.admincommands.impl.AdminChangeAccessLevel;
import l2ft.gameserver.handler.admincommands.impl.AdminClanHall;
import l2ft.gameserver.handler.admincommands.impl.AdminCreateItem;
import l2ft.gameserver.handler.admincommands.impl.AdminCursedWeapons;
import l2ft.gameserver.handler.admincommands.impl.AdminDelete;
import l2ft.gameserver.handler.admincommands.impl.AdminDisconnect;
import l2ft.gameserver.handler.admincommands.impl.AdminDoorControl;
import l2ft.gameserver.handler.admincommands.impl.AdminEditChar;
import l2ft.gameserver.handler.admincommands.impl.AdminEffects;
import l2ft.gameserver.handler.admincommands.impl.AdminEnchant;
import l2ft.gameserver.handler.admincommands.impl.AdminEvents;
import l2ft.gameserver.handler.admincommands.impl.AdminGeodata;
import l2ft.gameserver.handler.admincommands.impl.AdminGm;
import l2ft.gameserver.handler.admincommands.impl.AdminGmChat;
import l2ft.gameserver.handler.admincommands.impl.AdminHeal;
import l2ft.gameserver.handler.admincommands.impl.AdminHellbound;
import l2ft.gameserver.handler.admincommands.impl.AdminHelpPage;
import l2ft.gameserver.handler.admincommands.impl.AdminIP;
import l2ft.gameserver.handler.admincommands.impl.AdminInstance;
import l2ft.gameserver.handler.admincommands.impl.AdminKill;
import l2ft.gameserver.handler.admincommands.impl.AdminLevel;
import l2ft.gameserver.handler.admincommands.impl.AdminMammon;
import l2ft.gameserver.handler.admincommands.impl.AdminManor;
import l2ft.gameserver.handler.admincommands.impl.AdminMenu;
import l2ft.gameserver.handler.admincommands.impl.AdminMonsterRace;
import l2ft.gameserver.handler.admincommands.impl.AdminNochannel;
import l2ft.gameserver.handler.admincommands.impl.AdminOlympiad;
import l2ft.gameserver.handler.admincommands.impl.AdminPetition;
import l2ft.gameserver.handler.admincommands.impl.AdminPledge;
import l2ft.gameserver.handler.admincommands.impl.AdminPolymorph;
import l2ft.gameserver.handler.admincommands.impl.AdminQuests;
import l2ft.gameserver.handler.admincommands.impl.AdminReload;
import l2ft.gameserver.handler.admincommands.impl.AdminRepairChar;
import l2ft.gameserver.handler.admincommands.impl.AdminRes;
import l2ft.gameserver.handler.admincommands.impl.AdminRide;
import l2ft.gameserver.handler.admincommands.impl.AdminSS;
import l2ft.gameserver.handler.admincommands.impl.AdminScripts;
import l2ft.gameserver.handler.admincommands.impl.AdminServer;
import l2ft.gameserver.handler.admincommands.impl.AdminShop;
import l2ft.gameserver.handler.admincommands.impl.AdminShutdown;
import l2ft.gameserver.handler.admincommands.impl.AdminSkill;
import l2ft.gameserver.handler.admincommands.impl.AdminSpawn;
import l2ft.gameserver.handler.admincommands.impl.AdminTarget;
import l2ft.gameserver.handler.admincommands.impl.AdminTeleport;
import l2ft.gameserver.handler.admincommands.impl.AdminZone;
import l2ft.gameserver.model.Player;
import l2ft.gameserver.network.l2.components.CustomMessage;
import l2ft.gameserver.utils.Log;

public class AdminCommandHandler extends AbstractHolder
{
	private static final AdminCommandHandler _instance = new AdminCommandHandler();

	public static AdminCommandHandler getInstance()
	{
		return _instance;
	}

	private Map<String, IAdminCommandHandler> _datatable = new HashMap<String, IAdminCommandHandler>();

	private AdminCommandHandler()
	{
		registerAdminCommandHandler(new AdminAdmin());
		registerAdminCommandHandler(new AdminAnnouncements());
		registerAdminCommandHandler(new AdminBan());
		registerAdminCommandHandler(new AdminCamera());
		registerAdminCommandHandler(new AdminCancel());
		registerAdminCommandHandler(new AdminChangeAccessLevel());
		registerAdminCommandHandler(new AdminClanHall());
		registerAdminCommandHandler(new AdminCreateItem());
		registerAdminCommandHandler(new AdminCursedWeapons());
		registerAdminCommandHandler(new AdminDelete());
		registerAdminCommandHandler(new AdminDisconnect());
		registerAdminCommandHandler(new AdminDoorControl());
		registerAdminCommandHandler(new AdminEditChar());
		registerAdminCommandHandler(new AdminEffects());
		registerAdminCommandHandler(new AdminEnchant());
		registerAdminCommandHandler(new AdminEvents());
		registerAdminCommandHandler(new AdminGeodata());
		registerAdminCommandHandler(new AdminGm());
		registerAdminCommandHandler(new AdminGmChat());
		registerAdminCommandHandler(new AdminHeal());
		registerAdminCommandHandler(new AdminHellbound());
		registerAdminCommandHandler(new AdminHelpPage());
		registerAdminCommandHandler(new AdminInstance());
		registerAdminCommandHandler(new AdminIP());
		registerAdminCommandHandler(new AdminLevel());
		registerAdminCommandHandler(new AdminMammon());
		registerAdminCommandHandler(new AdminManor());
		registerAdminCommandHandler(new AdminMenu());
		registerAdminCommandHandler(new AdminMonsterRace());
		registerAdminCommandHandler(new AdminNochannel());
		registerAdminCommandHandler(new AdminOlympiad());
		registerAdminCommandHandler(new AdminPetition());
		registerAdminCommandHandler(new AdminPledge());
		registerAdminCommandHandler(new AdminPolymorph());
		registerAdminCommandHandler(new AdminQuests());
		registerAdminCommandHandler(new AdminReload());
		registerAdminCommandHandler(new AdminRepairChar());
		registerAdminCommandHandler(new AdminRes());
		registerAdminCommandHandler(new AdminRide());
		registerAdminCommandHandler(new AdminServer());
		registerAdminCommandHandler(new AdminShop());
		registerAdminCommandHandler(new AdminShutdown());
		registerAdminCommandHandler(new AdminSkill());
		registerAdminCommandHandler(new AdminScripts());
		registerAdminCommandHandler(new AdminSpawn());
		registerAdminCommandHandler(new AdminSS());
		registerAdminCommandHandler(new AdminTarget());
		registerAdminCommandHandler(new AdminTeleport());
		registerAdminCommandHandler(new AdminZone());
		registerAdminCommandHandler(new AdminKill());

	}

	public void registerAdminCommandHandler(IAdminCommandHandler handler)
	{
		for(Enum<?> e : handler.getAdminCommandEnum())
			_datatable.put(e.toString().toLowerCase(), handler);
	}

	public IAdminCommandHandler getAdminCommandHandler(String adminCommand)
	{
		String command = adminCommand;
		if(adminCommand.indexOf(" ") != -1)
			command = adminCommand.substring(0, adminCommand.indexOf(" "));
		return _datatable.get(command);
	}

	public void useAdminCommandHandler(Player activeChar, String adminCommand)
	{
		if(!(activeChar.isGM() || activeChar.getPlayerAccess().CanUseGMCommand))
		{
			activeChar.sendMessage(new CustomMessage("l2ft.gameserver.clientpackets.SendBypassBuildCmd.NoCommandOrAccess", activeChar).addString(adminCommand));
			return;
		}

		String[] wordList = adminCommand.split(" ");
		IAdminCommandHandler handler = _datatable.get(wordList[0]);
		if(handler != null)
		{
			boolean success = false;
			try
			{
				for(Enum<?> e : handler.getAdminCommandEnum())
					if(e.toString().equalsIgnoreCase(wordList[0]))
					{
						success = handler.useAdminCommand(e, wordList, adminCommand, activeChar);
						break;
					}
			}
			catch(Exception e)
			{
				error("", e);
			}

			Log.LogCommand(activeChar, activeChar.getTarget(), adminCommand, success);
		}
	}


	public void process()
	{

	}
	

	public int size()
	{
		return _datatable.size();
	}


	public void clear()
	{
		_datatable.clear();
	}

	/**
	 * Получение списка зарегистрированных админ команд
	 * @return список команд
	 */
	public Set<String> getAllCommands()
	{
		return _datatable.keySet();
	}
}