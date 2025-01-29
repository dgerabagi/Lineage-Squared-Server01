package npc.model.events;

import java.util.StringTokenizer;

import l2ft.gameserver.Config;
import l2ft.gameserver.data.htm.HtmCache;
import l2ft.gameserver.model.GameObjectsStorage;
import l2ft.gameserver.model.Player;
import l2ft.gameserver.model.instances.NpcInstance;
import l2ft.gameserver.network.l2.s2c.NpcHtmlMessage;
import l2ft.gameserver.scripts.Functions;
import l2ft.gameserver.templates.npc.NpcTemplate;
import l2ft.gameserver.utils.ItemFunctions;

import org.apache.commons.lang3.StringUtils;

public class FightClubManagerInstance extends NpcInstance
{
	private static final long serialVersionUID = 1L;

	private static final String HTML_INDEX = "scripts/events/fightclub/index.htm";
	private static final String HTML_ACCEPT = "scripts/events/fightclub/accept.htm";
	private static final String HTML_MAKEBATTLE = "scripts/events/fightclub/makebattle.htm";
	private static final String HTML_INFO = "scripts/events/fightclub/info.htm";
	private static final String HTML_DISABLED = "scripts/events/fightclub/disabled.htm";
	private static final String HTML_LIST = "scripts/events/fightclub/fightslist.htm";
	private static final String HTML_RESULT = "scripts/events/fightclub/result.htm";

	public FightClubManagerInstance(int objectId, NpcTemplate template)
	{
		super(objectId, template);
	}

	@Override
	public void onBypassFeedback(Player player, String command)
	{
		if(!canBypassCheck(player, this))
			return;

		if(!Config.FIGHT_CLUB_ENABLED)
		{
			player.sendPacket(new NpcHtmlMessage(player, this, HtmCache.getInstance().getNotNull(HTML_DISABLED, player), 0));
			return;
		}

		if(command.equalsIgnoreCase("index"))
		{
			player.sendPacket(new NpcHtmlMessage(player, this, HtmCache.getInstance().getNotNull(HTML_INDEX, player), 0));
		}

		else if(command.equalsIgnoreCase("makebattle"))
		{
			player.sendPacket(makeBattleHtml(player));
		}

		else if(command.equalsIgnoreCase("info"))
		{
			player.sendPacket(new NpcHtmlMessage(player, this, HtmCache.getInstance().getNotNull(HTML_INFO, player), 0));
		}

		else
		{
			final StringTokenizer st = new StringTokenizer(command, " ");
			String pageName = st.nextToken();
			if(pageName.equalsIgnoreCase("addbattle"))
			{
				int count = 0;
				try
				{
					count = Integer.parseInt(st.nextToken());
				}
				catch(NumberFormatException e)
				{
					sendResult(player, player.isLangRus() ? "ĐžŃ�Đ¸Đ±ĐşĐ°!" : "Error!", player.isLangRus() ? "Đ’Ń‹ Đ˝Đµ Đ˛Đ˛ĐµĐ»Đ¸ ĐşĐľĐ»Đ¸Ń‡ĐµŃ�Ń‚Đ˛Đľ, Đ¸Đ»Đ¸ Đ˝ĐµĐżŃ€Đ°Đ˛Đ¸Đ»ŃŚĐ˝ĐľĐµ Ń‡Đ¸Ń�Đ»Đľ." : "You did not enter the number or wrong number.");
				}
				String itemName = StringUtils.EMPTY;
				if(st.hasMoreTokens())
				{
					itemName = st.nextToken();
					while(st.hasMoreTokens())
						itemName += " " + st.nextToken();
				}
				Object[] objects = { player, itemName, count };
				final String respone = (String) callScripts("addApplication", objects);
				if("OK".equalsIgnoreCase(respone))
				{
					sendResult(player, player.isLangRus() ? "Đ’Ń‹ĐżĐľĐ»Đ˝ĐµĐ˝Đľ!" : "Completed!", (player.isLangRus() ? "Đ’Ń‹ Ń�ĐľĐ·Đ´Đ°Đ»Đ¸ Đ·Đ°ŃŹĐ˛ĐşŃ� Đ˝Đ° Ń�Ń‡Đ°Ń�Ń‚Đ¸Đµ.<br>Đ’Đ°Ń�Đ° Ń�Ń‚Đ°Đ˛ĐşĐ° - <font color=\"LEVEL\">" : "You have created an application for participation.<br>Your bet - <font color=\"LEVEL\">") + String.valueOf(objects[2]) + " " + String.valueOf(objects[1]) + (player.isLangRus() ? "</font><br><center>ĐŁĐ´Đ°Ń‡Đ¸!</center>" : "</font><br><center>Good luck!</center>"));
				}
				else if("NoItems".equalsIgnoreCase(respone))
				{
					sendResult(player, player.isLangRus() ? "ĐžŃ�Đ¸Đ±ĐşĐ°!" : "Error!", player.isLangRus() ? "ĐŁ Đ˛Đ°Ń� Đ˝ĐµĐ´ĐľŃ�Ń‚Đ°Ń‚ĐľŃ‡Đ˝Đľ Đ¸Đ»Đ¸ ĐľŃ‚Ń�Ń�Ń‚Ń�Ń‚Đ˛Ń�ŃŽŃ‚ Ń‚Ń€ĐµĐ±Ń�ŃŽŃ‰Đ¸ĐµŃ�ŃŹ ĐżŃ€ĐµĐ´ĐĽĐµŃ‚Ń‹!" : "You are not required or missing items!");
				}
				else if("reg".equalsIgnoreCase(respone))
				{
					sendResult(player, player.isLangRus() ? "ĐžŃ�Đ¸Đ±ĐşĐ°!" : "Error!", player.isLangRus() ? "Đ’Ń‹ Ń�Đ¶Đµ Đ·Đ°Ń€ĐµĐłĐ¸Ń�Ń‚Ń€Đ¸Ń€ĐľĐ˛Đ°Đ˝Ń‹! Đ•Ń�Đ»Đ¸ Đ˛Ń‹ Ń…ĐľŃ‚Đ¸Ń‚Đµ Đ¸Đ·ĐĽĐµĐ˝Đ¸Ń‚ŃŚ Ń�Ń‚Đ°Đ˛ĐşŃ�, Ń�Đ´Đ°Đ»Đ¸Ń‚Đµ Ń�Ń‚Đ°Ń€Ń�ŃŽ Ń€ĐµĐłĐ¸Ń�Ń‚Ń€Đ°Ń†Đ¸ŃŽ." : "You are already registered! If you wish to bid, remove the old registration.");
				}
			}
			else if(pageName.equalsIgnoreCase("delete"))
			{
				Object[] playerObject = { player };
				if((Boolean) callScripts("isRegistered", playerObject))
				{
					callScripts("deleteRegistration", playerObject);
					sendResult(player, player.isLangRus() ? "Đ’Ń‹ĐżĐľĐ»Đ˝ĐµĐ˝Đľ!" : "Completed!", player.isLangRus() ? "<center>Đ’Ń‹ Ń�Đ´Đ°Đ»ĐµĐ˝Ń‹ Đ¸Đ· Ń�ĐżĐ¸Ń�ĐşĐ° Ń€ĐµĐłĐ¸Ń�Ń‚Ń€Đ°Ń†Đ¸Đ¸.</center>" : "<center>You are removed from the list of registration.</center>");
				}
				else
					sendResult(player, player.isLangRus() ? "ĐžŃ�Đ¸Đ±ĐşĐ°!" : "Error!", player.isLangRus() ? "<center>Đ’Ń‹ Đ˝Đµ Đ±Ń‹Đ»Đ¸ Đ·Đ°Ń€ĐµĐłĐ¸Ń�Ń‚Ń€Đ¸Ń€ĐľĐ˛Đ°Đ˝Ń‹ Đ˝Đ° Ń�Ń‡Đ°Ń�Ń‚Đ¸Đµ.</center>" : "<center>You have not been registered to participate.</center>");
			}
			else if(pageName.equalsIgnoreCase("openpage"))
			{
				player.sendPacket(makeOpenPage(player, Integer.parseInt(st.nextToken())));
			}
			else if(pageName.equalsIgnoreCase("tryaccept"))
			{
				player.sendPacket(makeAcceptHtml(player, Long.parseLong(st.nextToken())));
			}
			else if(pageName.equalsIgnoreCase("accept"))
			{
				accept(player, Long.parseLong(st.nextToken()));
			}
		}
	}

	@Override
	public void showChatWindow(Player player, int val, Object... arg)
	{
		player.sendPacket(new NpcHtmlMessage(player, this, HtmCache.getInstance().getNotNull(HTML_INDEX, player), val));
	}

	private NpcHtmlMessage makeOpenPage(Player player, int pageId)
	{
		NpcHtmlMessage html = new NpcHtmlMessage(player, this);
		html.setFile(HTML_LIST);

		StringBuilder sb = new StringBuilder();

		final int count = (Integer) callScripts("getRatesCount", new Object[0]);

		int num = pageId * Config.PLAYERS_PER_PAGE;
		if(num > count)
			num = count;
		if(count > 0)
		{
			sb.append("<table width=300>");

			for(int i = pageId * Config.PLAYERS_PER_PAGE - Config.PLAYERS_PER_PAGE; i < num; i++)
			{
				Object[] index = { i };
				Rate rate = (Rate) callScripts("getRateByIndex", index);
				sb.append("<tr>");
				sb.append("<td align=center width=95>");
				sb.append("<a action=\"bypass -h npc_%objectId%_tryaccept ").append(rate.getStoredId()).append("\">");
				sb.append("<font color=\"ffff00\">").append(rate.getPlayerName()).append("</font></a></td>");
				sb.append("<td align=center width=70>").append(rate.getPlayerLevel()).append("</td>");
				sb.append("<td align=center width=100><font color=\"ff0000\">");
				sb.append(rate.getPlayerClass()).append("</font></td>");
				sb.append("<td align=center width=135><font color=\"00ff00\">");
				sb.append(rate.getItemCount()).append(" ").append(rate.getItemName());
				sb.append("</font></td></tr>");
			}

			sb.append("</table><br><br><br>");
			int pg = getPagesCount(count);
			sb.append("ĐˇŃ‚Ń€Đ°Đ˝Đ¸Ń†Ń‹:&nbsp;");
			for(int i = 1; i <= pg; i++)
			{
				if(i == pageId)
					sb.append(i).append("&nbsp;");
				else
					sb.append("<a action=\"bypass -h npc_%objectId%_openpage ").append(i).append("\">").append(i).append("</a>&nbsp;");
			}
		}
		else
			sb.append(player.isLangRus() ? "<br><center>ĐˇŃ‚Đ°Đ˛ĐľĐş ĐżĐľĐşĐ° Đ˝Đµ Ń�Đ´ĐµĐ»Đ°Đ˝Đľ</center>" : "<br><center>Rates have not yet done</center>");
		html.replace("%data%", sb.toString());

		return html;
	}

	private NpcHtmlMessage makeBattleHtml(Player player)
	{
		NpcHtmlMessage html = new NpcHtmlMessage(player, this);
		html.setFile(HTML_MAKEBATTLE);
		html.replace("%items%", (String) callScripts("getItemsList", new Object[0]));

		return html;
	}

	private NpcHtmlMessage makeAcceptHtml(Player player, long storedId)
	{
		Object[] id = { storedId };
		Rate rate = (Rate) callScripts("getRateByStoredId", id);
		NpcHtmlMessage html = new NpcHtmlMessage(player, this);
		html.setFile(HTML_ACCEPT);
		html.replace("%name%", rate.getPlayerName());
		html.replace("%class%", rate.getPlayerClass());
		html.replace("%level%", String.valueOf(rate.getPlayerLevel()));
		html.replace("%rate%", rate.getItemCount() + " " + rate.getItemName());
		html.replace("%storedId%", String.valueOf(rate.getStoredId()));
		return html;
	}

	private void accept(Player player, long storedId)
	{
		final Object[] data = { GameObjectsStorage.getAsPlayer(storedId), player };
		if(player.getStoredId() == storedId)
		{
			sendResult(player, player.isLangRus() ? "ĐžŃ�Đ¸Đ±ĐşĐ°!" : "Error!", player.isLangRus() ? "Đ’Ń‹ Đ˝Đµ ĐĽĐľĐ¶ĐµŃ‚Đµ Đ˛Ń‹Đ·Đ˛Đ°Ń‚ŃŚ Đ˝Đ° Đ±ĐľĐą Ń�Đ°ĐĽĐľĐłĐľ Ń�ĐµĐ±ŃŹ." : "You can not call the fight itself.");
			return;
		}
		//TODO: ĐźŃ€ĐľĐ˛ĐµŃ€ĐşĐ° Đ˝Đ° Đ°ĐąŃ‚ĐµĐĽŃ‹... Đ‘ĐµŃ€ĐµĐĽ ĐżŃ€Đ¸ĐĽĐµŃ€ Ń� doStart
		//if(Functions.getItemCount(player, _))
		if((Boolean) callScripts("requestConfirmation", data))
		{
			sendResult(player, player.isLangRus() ? "Đ’Đ˝Đ¸ĐĽĐ°Đ˝Đ¸Đµ!" : "Attention!", player.isLangRus() ? "Đ’Ń‹ ĐľŃ‚ĐżŃ€Đ°Đ˛Đ¸Đ»Đ¸ Đ·Đ°ĐżŃ€ĐľŃ� Ń�ĐľĐżĐµŃ€Đ˝Đ¸ĐşŃ�. Đ•Ń�Đ»Đ¸ Đ˛Ń�Đµ Ń�Ń�Đ»ĐľĐ˛Đ¸ŃŹ Ń�ĐľĐľŃ‚Đ˛ĐµŃ‚Ń�Ń‚Đ˛Ń�ŃŽŃ‚, Đ’Đ°Ń� ĐżĐµŃ€ĐµĐĽĐµŃ�Ń‚ŃŹŃ‚ Đ˝Đ° Đ°Ń€ĐµĐ˝Ń�<br><center><font color=\"LEVEL\">ĐŁĐ´Đ°Ń‡Đ¸!</font></center><br>" : "You have sent a request to the opponent. If all conditions match, you will move into the arena<br><center><font color=\"LEVEL\">Good luck!</font></center><br>");
		}
	}

	private void sendResult(Player player, String title, String text)
	{
		NpcHtmlMessage html = new NpcHtmlMessage(player, this);
		html.setFile(HtmCache.getInstance().getNotNull(HTML_RESULT, player));
		html.replace("%title%", title);
		html.replace("%text%", text);
		player.sendPacket(html);
	}

	private int getPagesCount(int count)
	{
		if(count % Config.PLAYERS_PER_PAGE > 0)
			return count / Config.PLAYERS_PER_PAGE + 1;
		return count / Config.PLAYERS_PER_PAGE;
	}

	private Object callScripts(String methodName, Object[] args)
	{
		return Functions.callScripts("events.FightClub.FightClubManager", methodName, args);
	}

	public static class Rate
	{
		private String playerName;
		private int playerLevel;
		private String playerClass;
		private int itemId;
		private String itemName;
		private int itemCount;
		private long playerStoredId;

		public Rate(Player player, int itemId, int itemCount)
		{
			playerName = player.getName();
			playerLevel = player.getLevel();
			playerClass = player.getActiveClassClassId().name();
			this.itemId = itemId;
			this.itemCount = itemCount;
			itemName = ItemFunctions.createItem(itemId).getTemplate().getName();
			playerStoredId = player.getStoredId();
		}

		public String getPlayerName()
		{
			return playerName;
		}

		public int getPlayerLevel()
		{
			return playerLevel;
		}

		public String getPlayerClass()
		{
			return playerClass;
		}

		public int getItemId()
		{
			return itemId;
		}

		public int getItemCount()
		{
			return itemCount;
		}

		public String getItemName()
		{
			return itemName;
		}

		public long getStoredId()
		{
			return playerStoredId;
		}
	}
}