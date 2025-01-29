package services.community;

import java.util.StringTokenizer;

import l2ft.gameserver.Config;
import l2ft.gameserver.data.htm.HtmCache;
import l2ft.gameserver.handler.bbs.CommunityBoardManager;
import l2ft.gameserver.handler.bbs.ICommunityBoardHandler;
import l2ft.gameserver.listener.actor.player.OnAnswerListener;
import l2ft.gameserver.model.Player;
import l2ft.gameserver.model.Zone.ZoneType;
import l2ft.gameserver.model.actor.instances.player.Unlocks.UnlockedClass;
import l2ft.gameserver.model.base.ClassId;
import l2ft.gameserver.model.base.Race;
import l2ft.gameserver.model.items.ItemInstance;
import l2ft.gameserver.network.l2.components.SystemMsg;
import l2ft.gameserver.network.l2.s2c.ConfirmDlg;
import l2ft.gameserver.network.l2.s2c.ShowBoard;
import l2ft.gameserver.network.l2.s2c.SocialAction;
import l2ft.gameserver.scripts.Functions;
import l2ft.gameserver.scripts.ScriptFile;
import l2ft.gameserver.utils.BbsUtil;

public class StackClassPage extends Functions implements ScriptFile, ICommunityBoardHandler {
	private static final String[] RACE_NAMES = { "Human", "Orc", "Dwarf", "Elf", "Dark Elf" };
	private static final Race[] RACE_NORMAL = { Race.human, Race.orc, Race.dwarf, Race.elf, Race.darkelf };
	private static final String[][] COLORS = { { "440000", "220000" }, { "994411", "CC7722" }, { "003377", "003377" },
			{ "335511", "333311" }, { "330055", "442255" } };

	private static final int NEW_CLASS_ITEM = 15632;
	private static final int NEEDED_ITEM_ID = 57;
	private static final int ITEM_COUNT = 100000;
	private static final String ITEM_NEEDED_MESSAGE = "You need 100k adena to change your class!";
	private static final String ASK_NEEDED_ITEM_MESSAGE = "Do you wish to switch class for 100k adena?";

	@Override
	public String[] getBypassCommands() {
		return new String[] { "_bbsgetfav", "_bbsstack", "_bbslink", };
	}

	@Override
	public void onBypassCommand(Player player, String bypass) {
		StringTokenizer st = new StringTokenizer(bypass, "_");
		String cmd = st.nextToken();
		if ("bbsgetfav".equals(cmd)) {
			showMainPage(player);
		} else if (cmd.startsWith("bbsstack")) {
			String option = st.nextToken();
			int classId = 0;
			if (st.hasMoreTokens())
				classId = Integer.parseInt(st.nextToken());

			if (option.equals("unlock")) {
				if (player.getInventory().destroyItemByItemId(NEW_CLASS_ITEM, 1)) {
					player.getUnlocks().addNewClass(classId, 1, 0);
					player.sendMessage("New Class has been unlocked!");
				} else
					player.sendMessage("You dont have Sub Class Reward Item!");
			} else if (option.equals("primary")) {
				if (!canChangeClass(player))
					return;
				ConfirmDlg packet = new ConfirmDlg(SystemMsg.S1, 60000).addString(ASK_NEEDED_ITEM_MESSAGE);
				player.ask(packet, new ClassChangeConfirm(player, option, classId));
			} else if (option.equals("secondary")) {
				if (!canChangeClass(player))
					return;
				ConfirmDlg packet = new ConfirmDlg(SystemMsg.S1, 60000).addString(ASK_NEEDED_ITEM_MESSAGE);
				player.ask(packet, new ClassChangeConfirm(player, option, classId));
			} else if (option.equals("swap")) {
				if (!canChangeClass(player))
					return;
				ConfirmDlg packet = new ConfirmDlg(SystemMsg.S1, 60000).addString(ASK_NEEDED_ITEM_MESSAGE);
				player.ask(packet, new ClassChangeConfirm(player, option, classId));
			} else if (option.equals("info")) {
				String html = HtmCache.getInstance().getNotNull(Config.BBS_HOME_DIR + "bbs_stackInfo.htm", player);

				html = BbsUtil.htmlAll(html, player);
				ShowBoard.separateAndSend(html, player);
				return;
			}
			showMainPage(player);
		} else if (bypass.equals("_bbsfbmain")) {
			// Here, we display the Forgotten Battlegrounds Dashboard.
			// For now, let's say you have a static HTML page:
			// bbs_forgotten_bg_dashboard.htm
			String html = HtmCache.getInstance().getNotNull(Config.BBS_HOME_DIR + "bbs_forgotten_bg_dashboard.htm",
					player);
			html = BbsUtil.htmlAll(html, player);
			ShowBoard.separateAndSend(html, player);
		}

	}

	public class ClassChangeConfirm implements OnAnswerListener {
		private Player _player;
		private String _option;
		private int _classId;

		public ClassChangeConfirm(Player player, String option, int classId) {
			_player = player;
			_option = option;
			_classId = classId;
		}

		@Override
		public void sayYes() {
			confirmChange(_player, _option, _classId);
		}

		@Override
		public void sayNo() {
		}
	}

	public void confirmChange(final Player player, String option, int classId) {
		if (!canChangeClass(player))
			return;
		if (!player.getInventory().destroyItemByItemId(NEEDED_ITEM_ID, ITEM_COUNT)) {
			player.sendMessage(ITEM_NEEDED_MESSAGE);
			return;
		}
		player.getEffectList().stopAllEffects();
		player.setCurrentMp(0);
		if (player.getPet() != null)
			player.getPet().unSummon();
		player.broadcastPacket(new SocialAction(player.getObjectId(), SocialAction.LEVEL_UP));

		if (option.equals("primary")) {
			player.switchStackClass(classId, true);
			player.sendMessage("Your primary class has been switched!");
		} else if (option.equals("secondary")) {
			player.switchStackClass(classId, false);
			player.sendMessage("Your secondary class has been switched!");
		} else if (option.equals("swap")) {
			if (ClassId.values()[player.getSecondaryClassId()].getLevel() < 4)
				return;
			player.swapStackClasses();
			player.sendMessage("Your main classes have been swapped!");
		}
		showMainPage(player);
	}

	public static boolean canChangeClass(Player player) {
		if (player.getActiveClass().isKamael() || player.getRace() == Race.kamael) {
			player.sendMessage("You can only change your class while being on main!");
			return false;
		}
		if (player.getZone(ZoneType.peace_zone) == null) {
			player.sendMessage("Class can be changed only in peace zone!");
			return false;
		}
		if (player.getCursedWeaponEquippedId() != 0) {
			player.sendMessage("You cannot change class right now!");
			return false;
		}
		if (player.isInOlympiadMode()) {
			player.sendMessage("You cannot change class right now!");
			return false;
		}

		return true;
	}

	private void showMainPage(Player player) {
		String html = HtmCache.getInstance().getNotNull(Config.BBS_HOME_DIR + "bbs_stackMain.htm", player);
		html = html.replace("%STACK_LIST%", getMainPage(player));

		html = BbsUtil.htmlAll(html, player);
		ShowBoard.separateAndSend(html, player);
	}

	private String getMainPage(Player player) {
		String html = getHeadline();
		int raceIndex = 0;
		int index = 0;
		Race[] races = player.getRace() == Race.kamael ? new Race[] {} : RACE_NORMAL;
		for (Race currentRace : races) {
			for (ClassId clazz : ClassId.values())
				if (clazz.getLevel() == 4 && clazz.getRace() == currentRace) {
					if (clazz == ClassId.maestro)
						continue;
					if (clazz.getRace() == Race.dwarf && index == 15) {
						html += "</td><td>";
						html += getHeadline();
					}
					html += "<table bgcolor=" + COLORS[raceIndex][index % 2] + " height=25><tr>";
					html += "<td align=left width=60>" + RACE_NAMES[raceIndex] + "</td>";
					html += "<td align=left width=120>" + getClassName(clazz) + "</td>";
					html += "<td align=left width=60>" + getLevel(player, clazz.getId()) + "</td>";
					html += "<td align=left width=70>" + getClassType(player, clazz.getId()) + "</td>";
					html += "<td align=center width=80>" + getClassUnlock(player, clazz) + "</td></tr></table>";
					index++;
				}
			raceIndex++;
		}
		return html;
	}

	private String getLevel(Player player, int classId) {
		UnlockedClass clazz = player.getUnlocks().getUnlockedClass(classId);
		if (clazz != null)
			return String.valueOf(clazz.getLevel());
		return "N/A";
	}

	private String getClassType(Player player, int classId) {
		if (player.getActiveClass().getFirstClassId() == classId)
			return "<font color=99FF44>Primary</font>";
		else if (player.getSecondaryClassId() == classId)
			return "<font color=33BBAA>Secondary</font>";
		else if (player.getUnlocks().getUnlockedClass(classId) != null)
			return "Unlocked";
		else
			return "<font color=FF9944>Locked</font>";
	}

	public String getClassUnlock(Player player, ClassId classId) {
		if (player.getUnlocks().getUnlockedClass(classId.getId()) != null && !player.getActiveClass().isKamael()
				&& player.getRace() != Race.kamael)
			if (player.getActiveClass().getFirstClassId() == classId.getId())// is Primary
				if (player.getSecondaryClassId() > 6)
					return "<button value=\"Swap\" action=\"bypass _bbsstack_swap_" + classId.getId()
							+ "\" width=40 height=20 back=\"L2UI_CT1.Button_DF_Down\" fore=\"L2UI_ct1.button_df\">";
				else
					return "- - - -";
			else if (player.getSecondaryClassId() == classId.getId()) // is Secondary
				return "<button value=\"Swap\" action=\"bypass _bbsstack_swap_" + classId.getId()
						+ "\" width=40 height=20 back=\"L2UI_CT1.Button_DF_Down\" fore=\"L2UI_ct1.button_df\">";
			else if (player.getActiveClassClassId().getRace() == classId.getRace()
					|| player.getActiveClassClassId().getRace() == Race.dwarf) {
				String buttons = "<table><tr><td>";
				buttons += "<button value=\"Prim\" action=\"bypass _bbsstack_primary_" + classId.getId()
						+ "\" width=40 height=20 back=\"L2UI_CT1.Button_DF_Down\" fore=\"L2UI_ct1.button_df\">";
				buttons += "</td><td>";
				buttons += "<button value=\"Sec\" action=\"bypass _bbsstack_secondary_" + classId.getId()
						+ "\" width=40 height=20 back=\"L2UI_CT1.Button_DF_Down\" fore=\"L2UI_ct1.button_df\">";
				buttons += "</td></tr></table>";
				return buttons;
			} else if (classId.getRace() == Race.dwarf)
				return "<button value=\"Sec\" action=\"bypass _bbsstack_secondary_" + classId.getId()
						+ "\" width=40 height=20 back=\"L2UI_CT1.Button_DF_Down\" fore=\"L2UI_ct1.button_df\">";
			else
				return "<button value=\"Prim\" action=\"bypass _bbsstack_primary_" + classId.getId()
						+ "\" width=40 height=20 back=\"L2UI_CT1.Button_DF_Down\" fore=\"L2UI_ct1.button_df\">";

		ItemInstance neededItem = player.getInventory().getItemByItemId(NEW_CLASS_ITEM);
		if (neededItem != null && neededItem.getCount() >= 1 && !player.getActiveClass().isKamael()
				&& player.getRace() != Race.kamael)
			return "<button value=\"Unlock\" action=\"bypass _bbsstack_unlock_" + classId.getId()
					+ "\" width=60 height=20 back=\"L2UI_CT1.Button_DF_Down\" fore=\"L2UI_ct1.button_df\">";
		else
			return "- - - -";
	}

	private String getHeadline() {
		return "<table bgcolor=444444 height=25><tr><td align=left width=60><font color=FFBB00>Race</td><td align=left width=120>Class</td><td align=left width=60>Level</td><td align=left width=70>Status</td><td align=center width=80>Options</font></td></tr></table>";
	}

	private String getClassName(ClassId classIndex) {
		switch (classIndex) {
			case phoenixKnight:
				return "Phoenix Knight";
			case hellKnight:
				return "Hell Knight";
			case arcanaLord:
				return "Arcana Lord";

			case evaTemplar:
				return "Eva's Templar";
			case swordMuse:
				return "Sword Muse";
			case windRider:
				return "Wind Rider";
			case moonlightSentinel:
				return "Moonlight Sentinel";
			case mysticMuse:
				return "Mystic Muse";
			case elementalMaster:
				return "Elemental Master";
			case evaSaint:
				return "Eva's Saint";

			case shillienTemplar:
				return "ShillenTemplar";
			case spectralDancer:
				return "Spectral Dancer";
			case ghostHunter:
				return "Ghost Hunter";
			case ghostSentinel:
				return "Ghost Sentinel";
			case stormScreamer:
				return "Storm Screamer";
			case spectralMaster:
				return "Spectral Master";
			case shillienSaint:
				return "Shillien Saint";

			case grandKhauatari:
				return "Grand Khauatari";

			case fortuneSeeker:
				return "Fortune Seeker";

			default:
				return classIndex.name().substring(0, 1).toUpperCase() + classIndex.name().substring(1);
		}
	}

	@Override
	public void onWriteCommand(Player player, String bypass, String arg1,
			String arg2, String arg3, String arg4, String arg5) {
	}

	@Override
	public void onLoad() {
		CommunityBoardManager.getInstance().registerHandler(this);
	}

	@Override
	public void onReload() {
		CommunityBoardManager.getInstance().removeHandler(this);
	}

	@Override
	public void onShutdown() {
	}
}