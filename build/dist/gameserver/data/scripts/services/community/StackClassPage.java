//
//  C:\l2sq\Pac Project\build\dist-dev\gameserver\data\scripts\services\community\StackClassPage.java
//
package services.community;

import java.util.StringTokenizer;
import java.util.List;
import java.util.ArrayList;

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
import l2ft.gameserver.utils.ItemFunctions;
import l2ft.gameserver.network.l2.s2c.SystemMessage;
import l2ft.gameserver.network.l2.s2c.PartySpelled;
import l2ft.gameserver.network.l2.s2c.RelationChanged;
import l2ft.gameserver.network.l2.s2c.ExSetCompassZoneCode;
import l2ft.gameserver.network.l2.s2c.TargetSelected;
import l2ft.gameserver.network.l2.s2c.TargetUnselected;
import l2ft.gameserver.model.Request;
import l2ft.gameserver.listener.actor.player.impl.ReviveAnswerListener;
import l2ft.gameserver.network.l2.components.CustomMessage;

/**
 * StackClassPage:
 * - Manages your unlocked classes,
 * - Allows switching primary/secondary or swapping them,
 * - etc.
 *
 * Restored "dwarf/spoiler" logic so dwarves can be chosen as secondary
 * regardless of your main race,
 * and can also do the normal same-race restrictions otherwise.
 */
public class StackClassPage extends Functions implements ScriptFile, ICommunityBoardHandler {
	private static final String[] RACE_NAMES = { "Human", "Orc", "Dwarf", "Elf", "Dark Elf" };
	private static final Race[] RACE_NORMAL = { Race.human, Race.orc, Race.dwarf, Race.elf, Race.darkelf };
	private static final String[][] COLORS = {
			{ "440000", "220000" },
			{ "994411", "CC7722" },
			{ "003377", "003377" },
			{ "335511", "333311" },
			{ "330055", "442255" }
	};

	private static final int NEW_CLASS_ITEM = 15632;
	private static final int NEEDED_ITEM_ID = 57;
	private static final int ITEM_COUNT = 100000;
	private static final String ITEM_NEEDED_MESSAGE = "You need 100k adena to change your class!";
	private static final String ASK_NEEDED_ITEM_MESSAGE = "Do you wish to switch class for 100k adena?";

	@Override
	public String[] getBypassCommands() {
		return new String[] {
				"_bbsgetfav",
				"_bbsstack",
				"_bbslink",
		};
	}

	@Override
	public void onBypassCommand(Player player, String bypass) {
		// Check for a space-based approach: _bbslink main.htm
		if (bypass.startsWith("_bbslink ")) {
			StringTokenizer spaceSt = new StringTokenizer(bypass, " ");
			if (!spaceSt.hasMoreTokens())
				return;
			String firstToken = spaceSt.nextToken(); // _bbslink
			if (!"_bbslink".equals(firstToken))
				return;
			if (spaceSt.hasMoreTokens()) {
				String link = spaceSt.nextToken();
				String path = Config.BBS_HOME_DIR + "autofarm/" + link;
				String html = HtmCache.getInstance().getNotNull(path, player);
				html = BbsUtil.htmlAll(html, player);
				ShowBoard.separateAndSend(html, player);
			} else {
				player.sendMessage("Invalid link usage! (Please specify something like main.htm)");
			}
			return;
		}

		// Otherwise underscore-based commands:
		StringTokenizer st = new StringTokenizer(bypass, "_");
		String cmd = st.nextToken(); // e.g. "bbsstack" or "bbsgetfav"

		if ("bbsgetfav".equals(cmd)) {
			showMainPage(player);
		} else if (cmd.startsWith("bbsstack")) {
			String option = st.nextToken(); // e.g. "unlock","primary","swap","info"
			int classId = 0;
			if (st.hasMoreTokens())
				classId = Integer.parseInt(st.nextToken());

			if (option.equals("unlock")) {
				// Try to unlock a new class if the player has the special item
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
				if (ClassId.values()[player.getSecondaryClassId()].getLevel() < 4)
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
		} else if (bypass.equals("_bbsPVPBattlegrounds")) {
			String html = HtmCache.getInstance().getNotNull(Config.BBS_HOME_DIR + "bbs_pvp_bg_dashboard.htm", player);
			html = BbsUtil.htmlAll(html, player);
			ShowBoard.separateAndSend(html, player);
		}
	}

	/**
	 * Checks if player is in correct conditions to do a class switch.
	 */
	public static boolean canChangeClass(Player player) {
		if (player.getActiveClass().isKamael() || player.getRace() == Race.kamael) {
			player.sendMessage("You can only change your class while being on main!");
			return false;
		}
		if (player.getZone(ZoneType.peace_zone) == null) {
			player.sendMessage("Class can be changed only in a Peace Zone!");
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

	/**
	 * The OnAnswerListener for confirm dialogs about switching class.
	 */
	public class ClassChangeConfirm implements OnAnswerListener {
		private final Player _player;
		private final String _option; // "primary", "secondary", or "swap"
		private final int _classId;

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
			// do nothing
		}
	}

	/**
	 * Actually change the player's class (primary, secondary, or swap).
	 */
	private void confirmChange(Player player, String option, int classId) {
		System.out.println("[StackClassPage] confirmChange() called: Player=" + player.getName() +
				", option=" + option + ", classId=" + classId);

		if (!canChangeClass(player)) {
			System.out.println("[StackClassPage] canChangeClass() returned false. Aborting.");
			return;
		}

		// Make sure the player pays the required adena
		if (!player.getInventory().destroyItemByItemId(NEEDED_ITEM_ID, ITEM_COUNT)) {
			player.sendMessage(ITEM_NEEDED_MESSAGE);
			System.out.println("[StackClassPage] Not enough adena. Needed " + ITEM_COUNT + ", aborting.");
			return;
		}

		// Clear buffs, set mp=0, unsummon pet, social action
		System.out.println("[StackClassPage] Removing all buffs, unsummoning pet, etc.");
		player.getEffectList().stopAllEffects();
		player.setCurrentMp(0);
		if (player.getPet() != null)
			player.getPet().unSummon();
		player.broadcastPacket(new SocialAction(player.getObjectId(), SocialAction.LEVEL_UP));

		// Then do the actual operation:
		System.out.println("[StackClassPage] Doing operation: " + option + " for classId=" + classId);
		if (option.equals("primary")) {
			player.switchStackClass(classId, true);
			player.sendMessage("Your primary class has been switched!");
		} else if (option.equals("secondary")) {
			player.switchStackClass(classId, false);
			player.sendMessage("Your secondary class has been switched!");
		} else if (option.equals("swap")) {
			if (ClassId.values()[player.getSecondaryClassId()].getLevel() < 4) {
				System.out.println("[StackClassPage] Secondary class is not 4th level, cannot swap. Aborting.");
				return;
			}
			player.swapStackClasses();
			player.sendMessage("Your main classes have been swapped!");
		}

		System.out.println("[StackClassPage] confirmChange() finished for " + player.getName());
		showMainPage(player);
	}

	/**
	 * The main page showing all 4th-level classes for the player's race.
	 */
	private void showMainPage(Player player) {
		String html = HtmCache.getInstance().getNotNull(Config.BBS_HOME_DIR + "bbs_stackMain.htm", player);
		html = html.replace("%STACK_LIST%", getClassTable(player));
		html = BbsUtil.htmlAll(html, player);
		ShowBoard.separateAndSend(html, player);
	}

	/**
	 * Build the class table listing for the player's current scenario,
	 * respecting normal same-race rules except dwarves are always allowed as
	 * secondary.
	 */
	private String getClassTable(Player player) {
		StringBuilder sb = new StringBuilder();
		sb.append(getHeadline());

		int raceIndex = 0;
		int index = 0;

		// If kamael, skip
		Race[] allowed = (player.getRace() == Race.kamael) ? new Race[] {} : RACE_NORMAL;
		for (int r = 0; r < allowed.length; r++) {
			Race currentRace = allowed[r];
			for (int i = 0; i < ClassId.values().length; i++) {
				ClassId clazz = ClassId.values()[i];
				if (clazz.getLevel() == 4 && clazz.getRace() == currentRace) {
					if (clazz == ClassId.maestro)
						continue;

					if (clazz.getRace() == Race.dwarf && index == 15) {
						sb.append("</td><td>");
						sb.append(getHeadline());
					}

					sb.append("<table bgcolor=").append(COLORS[r][index % 2]).append(" height=25><tr>");
					sb.append("<td align=left width=60>").append(RACE_NAMES[r]).append("</td>");
					sb.append("<td align=left width=120>").append(getClassName(clazz)).append("</td>");
					sb.append("<td align=left width=60>").append(getLevel(player, clazz.getId())).append("</td>");
					sb.append("<td align=left width=70>").append(getClassStatus(player, clazz.getId())).append("</td>");
					sb.append("<td align=center width=80>").append(getClassOptions(player, clazz)).append("</td>");
					sb.append("</tr></table>");
					index++;
				}
			}
			raceIndex++;
		}

		return sb.toString();
	}

	private String getHeadline() {
		return "<table bgcolor=444444 height=25><tr>"
				+ "<td align=left width=60><font color=FFBB00>Race</font></td>"
				+ "<td align=left width=120>Class</td>"
				+ "<td align=left width=60>Level</td>"
				+ "<td align=left width=70>Status</td>"
				+ "<td align=center width=80>Options</td>"
				+ "</tr></table>";
	}

	private String getLevel(Player player, int classId) {
		UnlockedClass uc = player.getUnlocks().getUnlockedClass(classId);
		if (uc != null)
			return String.valueOf(uc.getLevel());
		return "N/A";
	}

	private String getClassStatus(Player player, int classId) {
		if (player.getActiveClass().getFirstClassId() == classId)
			return "<font color=99FF44>Primary</font>";
		else if (player.getSecondaryClassId() == classId)
			return "<font color=33BBAA>Secondary</font>";
		else if (player.getUnlocks().getUnlockedClass(classId) != null)
			return "Unlocked";
		else
			return "<font color=FF9944>Locked</font>";
	}

	/**
	 * The heart of the logic: dwarves can be chosen as secondary by ANY other race,
	 * and if you are dwarf, you can also pick other dwarven classes for
	 * primary/secondary normally.
	 */
	private String getClassOptions(Player player, ClassId cid) {
		int cId = cid.getId();
		UnlockedClass unlocked = player.getUnlocks().getUnlockedClass(cId);

		// If not unlocked, show "Unlock" button if the player has the new class item
		if (unlocked == null) {
			ItemInstance neededItem = player.getInventory().getItemByItemId(NEW_CLASS_ITEM);
			if (neededItem != null && neededItem.getCount() >= 1 && !player.getActiveClass().isKamael()
					&& player.getRace() != Race.kamael) {
				return "<button value=\"Unlock\" action=\"bypass _bbsstack_unlock_" + cId
						+ "\" width=60 height=20 back=\"L2UI_CT1.Button_DF_Down\" "
						+ "fore=\"L2UI_ct1.button_df\">";
			}
			return "---";
		}

		// If it's currently the active main:
		if (player.getActiveClass().getFirstClassId() == cId) {
			// If we do have some secondary set, show "Swap" button
			if (player.getSecondaryClassId() > 6) {
				return "<button value=\"Swap\" action=\"bypass _bbsstack_swap_" + cId
						+ "\" width=40 height=20 back=\"L2UI_CT1.Button_DF_Down\" "
						+ "fore=\"L2UI_ct1.button_df\">";
			} else {
				return "---";
			}
		}
		// If it's your active secondary:
		else if (player.getSecondaryClassId() == cId) {
			return "<button value=\"Swap\" action=\"bypass _bbsstack_swap_" + cId
					+ "\" width=40 height=20 back=\"L2UI_CT1.Button_DF_Down\" "
					+ "fore=\"L2UI_ct1.button_df\">";
		}
		// Otherwise: It's unlocked but not currently used => we can choose primary or
		// secondary,
		// subject to the dwarven special logic.
		else {
			Race mainRace = ClassId.values()[player.getActiveClassClassId().getId()].getRace();
			Race thisClassRace = cid.getRace();

			// If dwarf: always show secondary button for ANY main race
			if (thisClassRace == Race.dwarf) {
				// If your main is also dwarf, we can show both "Prim" and "Sec"
				if (mainRace == Race.dwarf) {
					return "<table><tr>"
							+ "<td><button value=\"Prim\" action=\"bypass _bbsstack_primary_" + cId
							+ "\" width=40 height=20 back=\"L2UI_CT1.Button_DF_Down\" fore=\"L2UI_ct1.button_df\"></td>"
							+ "<td><button value=\"Sec\" action=\"bypass _bbsstack_secondary_" + cId
							+ "\" width=40 height=20 back=\"L2UI_CT1.Button_DF_Down\" fore=\"L2UI_ct1.button_df\"></td>"
							+ "</tr></table>";
				}
				// otherwise show secondary only
				return "<button value=\"Sec\" action=\"bypass _bbsstack_secondary_" + cId
						+ "\" width=40 height=20 back=\"L2UI_CT1.Button_DF_Down\" "
						+ "fore=\"L2UI_ct1.button_df\">";
			}
			// If your main race is dwarf: you can always pick ANY unlocked class as
			// secondary,
			// or also as primary if the new class is same race (dwarf->dwarf).
			if (mainRace == Race.dwarf) {
				// If same race, show both
				if (thisClassRace == Race.dwarf) {
					return "<table><tr>"
							+ "<td><button value=\"Prim\" action=\"bypass _bbsstack_primary_" + cId
							+ "\" width=40 height=20 back=\"L2UI_CT1.Button_DF_Down\" fore=\"L2UI_ct1.button_df\"></td>"
							+ "<td><button value=\"Sec\" action=\"bypass _bbsstack_secondary_" + cId
							+ "\" width=40 height=20 back=\"L2UI_CT1.Button_DF_Down\" fore=\"L2UI_ct1.button_df\"></td>"
							+ "</tr></table>";
				}
				// else race mismatch but your main is dwarf, so we can do "Sec" only
				return "<button value=\"Sec\" action=\"bypass _bbsstack_secondary_" + cId
						+ "\" width=40 height=20 back=\"L2UI_CT1.Button_DF_Down\" "
						+ "fore=\"L2UI_ct1.button_df\">";
			}

			// else normal logic if same race as main => show both prim + sec
			if (thisClassRace == mainRace) {
				StringBuilder str = new StringBuilder();
				str.append("<table><tr>");
				str.append("<td><button value=\"Prim\" action=\"bypass _bbsstack_primary_").append(cId)
						.append("\" width=40 height=20 back=\"L2UI_CT1.Button_DF_Down\" fore=\"L2UI_ct1.button_df\">");
				str.append("</td>");
				str.append("<td><button value=\"Sec\" action=\"bypass _bbsstack_secondary_").append(cId)
						.append("\" width=40 height=20 back=\"L2UI_CT1.Button_DF_Down\" fore=\"L2UI_ct1.button_df\">");
				str.append("</td>");
				str.append("</tr></table>");
				return str.toString();
			}
			// else different race -> show only "Prim" button (like normal logic)
			return "<table><tr><td>"
					+ "<button value=\"Prim\" action=\"bypass _bbsstack_primary_" + cId
					+ "\" width=40 height=20 back=\"L2UI_CT1.Button_DF_Down\" fore=\"L2UI_ct1.button_df\">"
					+ "</td></tr></table>";
		}
	}

	private String getClassName(ClassId cid) {
		switch (cid) {
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
				return "Shillien Templar";
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
			default: {
				String name = cid.name();
				// Capitalize
				return name.substring(0, 1).toUpperCase() + name.substring(1);
			}
		}
	}

	@Override
	public void onWriteCommand(Player player, String bypass, String arg1, String arg2,
			String arg3, String arg4, String arg5) {
		// not used here
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
