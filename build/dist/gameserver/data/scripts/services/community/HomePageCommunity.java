package services.community;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.StringTokenizer;

import l2ft.gameserver.Config;
import l2ft.gameserver.data.htm.HtmCache;
import l2ft.gameserver.handler.bbs.CommunityBoardManager;
import l2ft.gameserver.handler.bbs.ICommunityBoardHandler;
import l2ft.gameserver.instancemanager.ReflectionManager;
import l2ft.gameserver.listener.actor.player.OnAnswerListener;
import l2ft.gameserver.model.GameObjectsStorage;
import l2ft.gameserver.model.Party;
import l2ft.gameserver.model.Player;
import l2ft.gameserver.model.Zone.ZoneType;
import l2ft.gameserver.model.actor.instances.player.Unlocks.UnlockedClass;
import l2ft.gameserver.model.base.ClassId;
import l2ft.gameserver.model.base.Experience;
import l2ft.gameserver.model.base.Race;
import l2ft.gameserver.network.l2.components.SystemMsg;
import l2ft.gameserver.network.l2.s2c.ConfirmDlg;
import l2ft.gameserver.network.l2.s2c.JoinParty;
import l2ft.gameserver.network.l2.s2c.NpcHtmlMessage;
import l2ft.gameserver.network.l2.s2c.ShowBoard;
import l2ft.gameserver.scripts.Functions;
import l2ft.gameserver.scripts.ScriptFile;
import l2ft.gameserver.taskmanager.tasks.TaskVariable.TaskType;
import l2ft.gameserver.utils.BbsUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A unified PartyMatching system that uses two .htm pages:
 * - bbs_homepage.htm (older style, triggered by _bbslink)
 * - bbs_partymatching.htm (new style, triggered by _bbsPartymatching)
 *
 * We produce two sets of "getCharacters" methods, so that the "Classes" button
 * can call the correct bypass (_bbslink_ vs _bbsPartymatching_).
 */
public class HomePageCommunity extends Functions implements ScriptFile, ICommunityBoardHandler {
	private static final Logger _log = LoggerFactory.getLogger(HomePageCommunity.class);

	private static final int CHECKED_COUNT = 9; // # of checkboxes
	private static final int MAX_PER_PAGE = 20; // pagination limit

	// Short name references for stack classes, used in getNotChosenClasses(), etc.
	private static final String[] ALL_CLASS_SHORT_NAMES = {
			"Duelist", "Dreadnought", "PhoenixKnight", "HellKnight", "Adventurer", "Saggitarius",
			"Archmage", "SoulTaker", "ArcanaLord", "Cardinal", "Hierophant", "EvaTemplar", "SwordMuse",
			"WindRider", "MoonlightSentine", "MysticMuse", "ElementalMaster", "EvaSaint", "ShillienTemplar",
			"SpectralDancer", "GhostHunter", "GhostSentinel", "StormScreamer", "SpectralMaster",
			"ShillienSaint", "Titan", "GrandKhauatari", "Dominator", "Doomcryer", "FortuneSeeker"
	};

	@Override
	public String[] getBypassCommands() {
		// We handle: _bbslink, _bbsclass, _bbsPartymatching
		return new String[] { "_bbslink", "_bbsclass", "_bbsPartymatching" };
	}

	@Override
	public void onBypassCommand(Player player, String bypass) {
		if (player == null)
			return;

		StringTokenizer st = new StringTokenizer(bypass, "_");
		String mainToken = st.nextToken(); // "bbslink", "bbsclass", or "bbsPartymatching"

		if ("bbslink".equals(mainToken)) {
			// The older homepage approach (using bbs_homepage.htm)
			if (!st.hasMoreTokens()) {
				showMainPage(player, 0, 0, 0, 0, 0, 0);
				return;
			}

			int classesSortType = Integer.parseInt(st.nextToken());
			int sortType = Integer.parseInt(st.nextToken());
			int asc = Integer.parseInt(st.nextToken());
			int page = Integer.parseInt(st.nextToken());
			int charObjId = Integer.parseInt(st.nextToken());
			int classPage = Integer.parseInt(st.nextToken());

			showMainPage(player, classesSortType, sortType, asc, page, charObjId, classPage);

			// Possibly an extra token => toggle hide (-1) or invite with classID
			if (st.hasMoreTokens()) {
				int nextNumber = Integer.parseInt(st.nextToken());
				if (nextNumber == -1) {
					boolean oldState = player.isPartyMatchingVisible();
					player.setPartyMatchingVisible();
					if (player.isPartyMatchingVisible())
						player.sendMessage("You are now visible on Party Matching list!");
					else
						player.sendMessage("You are NO LONGER visible on Party Matching list!");

					showMainPage(player, classesSortType, sortType, asc, page, charObjId, classPage);
				} else {
					// Invite to party with nextNumber as classId
					Player invited = GameObjectsStorage.getPlayer(charObjId);
					if (invited != null && player != invited && invited.getParty() == null) {
						String partyMsg = canJoinParty(invited);
						if (partyMsg.isEmpty()) {
							ClassId clazz = ClassId.values()[nextNumber];
							ConfirmDlg packet = new ConfirmDlg(SystemMsg.S1, 60000)
									.addString("Do you want to join " + player.getName()
											+ " party as " + getFullClassName(clazz) + "?");
							invited.ask(packet, new InviteAnswer(invited, player, clazz));
							player.sendMessage("Invitation has been sent!");
						} else
							player.sendMessage(partyMsg);
					}
				}
			}
		} else if ("bbsclass".equals(mainToken)) {
			// StackClass logic
			if (StackClassPage.canChangeClass(player)) {
				String type = st.nextToken(); // "Primary", "Secondary", or "secondChoise"
				int inviterObjectId = Integer.parseInt(st.nextToken());

				if ("Primary".equals(type) || "Secondary".equals(type)) {
					int classId = Integer.parseInt(st.nextToken());
					boolean primary = "Primary".equals(type);

					player.switchStackClass(classId, primary);
					showSecondChooseClassPage(player, inviterObjectId, ClassId.values()[classId], primary);
				} else if ("secondChoise".equals(type)) {
					Player inviter = GameObjectsStorage.getPlayer(inviterObjectId);
					if (inviter == null && player.getParty() != null)
						inviter = player.getParty().getPartyLeader();

					boolean nowSecondary = (Integer.parseInt(st.nextToken()) == 1);
					String choosenClassName = st.nextToken();
					// remove leading space
					choosenClassName = choosenClassName.substring(1);

					int choosenClassId = 0;
					int idx = 0;
					for (String name : ALL_CLASS_SHORT_NAMES) {
						if (choosenClassName.equals(name))
							choosenClassId = idx;
						idx++;
					}
					choosenClassId += 88;

					player.switchStackClass(choosenClassId, !nowSecondary);
					player.sendPacket(new NpcHtmlMessage(0).setHtml(""));
					askForTeleport(player, inviter);
				}
			}
		} else if ("bbsPartymatching".equals(mainToken)) {
			// The new partymatching approach (using bbs_partymatching.htm)
			if (!st.hasMoreTokens()) {
				// no params => default
				showPartyMatchingPage(player, 0, 0, 0, 0, 0, 0);
			} else {
				int classesSortType = Integer.parseInt(st.nextToken());
				int sortType = Integer.parseInt(st.nextToken());
				int asc = Integer.parseInt(st.nextToken());
				int page = Integer.parseInt(st.nextToken());
				int charObjId = Integer.parseInt(st.nextToken());
				int classPage = Integer.parseInt(st.nextToken());

				showPartyMatchingPage(player, classesSortType, sortType, asc, page, charObjId, classPage);

				if (st.hasMoreTokens()) {
					int nextNumber = Integer.parseInt(st.nextToken());
					if (nextNumber == -1) {
						boolean oldState = player.isPartyMatchingVisible();
						player.setPartyMatchingVisible();
						if (player.isPartyMatchingVisible())
							player.sendMessage("You are now visible on Party Matching list!");
						else
							player.sendMessage("You are NO LONGER visible on Party Matching list!");

						showPartyMatchingPage(player, classesSortType, sortType, asc, page, charObjId, classPage);
					} else {
						// That means user clicked "Invite" after picking someone's class
						Player invited = GameObjectsStorage.getPlayer(charObjId);
						if (invited != null && player != invited && invited.getParty() == null) {
							String partyMsg = canJoinParty(invited);
							if (partyMsg.isEmpty()) {
								ClassId clazz = ClassId.values()[nextNumber];
								ConfirmDlg packet = new ConfirmDlg(SystemMsg.S1, 60000)
										.addString("Do you want to join " + player.getName()
												+ " party as " + getFullClassName(clazz) + "?");
								invited.ask(packet, new InviteAnswer(invited, player, clazz));
								player.sendMessage("Invitation has been sent!");
							} else
								player.sendMessage(partyMsg);
						}
					}
				}
			}
		}
	}

	// -------------------------------------------------------------------------
	// bbs_homepage logic
	// -------------------------------------------------------------------------
	private void showMainPage(Player player, int classesSortType, int sortType,
			int asc, int page, int charObjId, int classPage) {
		String html = HtmCache.getInstance().getNotNull(Config.BBS_HOME_DIR + "bbs_homepage.htm", player);

		// We use the homepage method => getCharactersHomepage
		String charactersHtml = getCharactersHomepage(player, sortType, asc, classesSortType, page, charObjId);
		html = html.replace("%characters%", charactersHtml);

		String classesHtml = getClassesHomepage(charObjId, classPage);
		html = html.replace("%classes%", classesHtml);

		html = html.replace("%visible%", player.isPartyMatchingVisible() ? "Hide from list" : "Show on list");
		html = replaceCommon(html, classesSortType, sortType, asc, page, charObjId, classPage);

		// fill checkboxes
		for (int i = 0; i < CHECKED_COUNT; i++)
			html = html.replace("%checked" + i + "%", getChecked(i, classesSortType));

		html = BbsUtil.htmlAll(html, player);
		ShowBoard.separateAndSend(html, player);
	}

	/**
	 * Original getCharacters method for homepage, using "_bbslink" in the "Classes"
	 * button.
	 */
	private String getCharactersHomepage(Player visitor, int charSort, int asc,
			int classSort, int page, int charToView) {
		List<Player> allPlayers = getPlayerList(visitor, charSort, asc, classSort);
		int badCharacters = 0;
		boolean nextPageOk = true;
		String html = "";

		for (int i = MAX_PER_PAGE * page; i < (MAX_PER_PAGE + badCharacters + page * MAX_PER_PAGE); i++) {
			if (i >= allPlayers.size()) {
				nextPageOk = false;
				break;
			}
			Player p = allPlayers.get(i);

			// Filter
			if (!isClassTestPassed(p, classSort)) {
				badCharacters++;
				continue;
			}
			int maxLevel = getMaxLevel(p, classSort);
			int unlocksSize = getUnlocksSize(p, classSort);

			html += "<table bgcolor=" + getLineColor((charToView == p.getObjectId()) ? 2 : 0, i)
					+ " width=425 border=0 cellpadding=0 cellspacing=0><tr>";
			html += "<td width=200><center><font color=cacd81>" + p.getName() + "</font></center></td>";
			html += "<td width=80><center><font color=cacd81>" + maxLevel + "</font></center></td>";
			html += "<td width=70><center><font color=cacd81>" + unlocksSize + "</font></center></td>";

			// "Classes" button => calls _bbslink
			html += "<td width=75>"
					+ "<button value=\"Classes\" action=\"bypass _bbslink_%class%_%sort%_%asc%_%page%_"
					+ p.getObjectId()
					+ "_0\" width=80 height=18 back=\"L2UI_CT1.Button_DF_Down\" fore=\"L2UI_ct1.button_df\">"
					+ "</td>";

			html += "</tr></table>";
		}

		// Pagination
		html += "<center><table><tr>";
		if (page > 0) {
			html += "<td><button value=\"Prev\" action=\"bypass _bbslink_%class%_%sort%_%asc%_"
					+ (page - 1)
					+ "_%char%_%classPage%\" width=80 height=18 back=\"L2UI_CT1.Button_DF_Down\" "
					+ "fore=\"L2UI_ct1.button_df\"></td>";
		}
		if (nextPageOk) {
			html += "<td><button value=\"Next\" action=\"bypass _bbslink_%class%_%sort%_%asc%_"
					+ (page + 1)
					+ "_%char%_%classPage%\" width=80 height=18 back=\"L2UI_CT1.Button_DF_Down\" "
					+ "fore=\"L2UI_ct1.button_df\"></td>";
		}
		html += "</tr></table></center>";

		return html;
	}

	/**
	 * Original getClasses method for homepage, also uses _bbslink in the "Invite"
	 * button.
	 */
	private String getClassesHomepage(int charObjId, int page) {
		if (charObjId == 0)
			return "";

		Player playerToView = GameObjectsStorage.getPlayer(charObjId);
		if (playerToView == null)
			return "";

		String html = "";
		int i = 0;
		boolean nextPageOk = false;

		for (Object obj : playerToView.getUnlocks().getAllUnlocks().getValues()) {
			UnlockedClass clazz = (UnlockedClass) obj;
			if (ClassId.values()[clazz.getId()].getLevel() < 4)
				continue;
			if (ClassId.values()[clazz.getId()].getRace() == Race.kamael)
				continue;

			if (i < MAX_PER_PAGE * page) {
				i++;
				continue;
			}
			if (i >= (MAX_PER_PAGE + page * MAX_PER_PAGE)) {
				nextPageOk = true;
				break;
			}

			int type;
			if (clazz.getId() == playerToView.getActiveClassClassId().getId())
				type = 2;
			else if (clazz.getId() == playerToView.getSecondaryClassId())
				type = 1;
			else
				type = 0;

			html += "<table bgcolor=" + getLineColor(type, i) + " width=320 border=0 cellpadding=0 cellspacing=0>";
			html += "<tr>";
			html += "<td width=150><center><font color=cacd81>"
					+ getFullClassName(ClassId.values()[clazz.getId()]) + "</font></center></td>";
			html += "<td width=80><center><font color=cacd81>" + clazz.getLevel() + "</font></center></td>";

			// "Invite" button => _bbslink
			html += "<td width=90>"
					+ "<button value=\"Invite\" action=\"bypass _bbslink_%class%_%sort%_%asc%_%page%_%char%_%classPage%_"
					+ clazz.getId()
					+ "\" width=80 height=18 back=\"L2UI_CT1.Button_DF_Down\" fore=\"L2UI_ct1.button_df\">"
					+ "</td>";
			html += "</tr></table>";
			i++;
		}

		// Pagination
		html += "<center><table width=320 border=0 cellpadding=0 cellspacing=0><tr>";
		if (page > 0) {
			html += "<td><center>"
					+ "<button value=\"Prev\" action=\"bypass _bbslink_%class%_%sort%_%asc%_%page%_%char%_"
					+ (page - 1)
					+ "\" width=80 height=18 back=\"L2UI_CT1.Button_DF_Down\" fore=\"L2UI_ct1.button_df\">"
					+ "</center></td>";
		}
		if (nextPageOk) {
			html += "<td><center>"
					+ "<button value=\"Next\" action=\"bypass _bbslink_%class%_%sort%_%asc%_%page%_%char%_"
					+ (page + 1)
					+ "\" width=80 height=18 back=\"L2UI_CT1.Button_DF_Down\" fore=\"L2UI_ct1.button_df\">"
					+ "</center></td>";
		}
		html += "</tr></table></center>";

		return html;
	}

	// -------------------------------------------------------------------------
	// bbs_partymatching logic
	// -------------------------------------------------------------------------
	private void showPartyMatchingPage(Player player, int classesSortType, int sortType,
			int asc, int page, int charObjId, int classPage) {
		String html = HtmCache.getInstance().getNotNull(Config.BBS_HOME_DIR + "bbs_partymatching.htm", player);

		// For partymatching, we use the "Partymatching" version of these methods
		String charactersHtml = getCharactersPartymatching(player, sortType, asc, classesSortType, page, charObjId);
		html = html.replace("%characters%", charactersHtml);

		String classesHtml = getClassesPartymatching(charObjId, classPage);
		html = html.replace("%classes%", classesHtml);

		html = html.replace("%visible%", player.isPartyMatchingVisible() ? "Hide from list" : "Show on list");
		html = replaceCommon(html, classesSortType, sortType, asc, page, charObjId, classPage);

		for (int i = 0; i < CHECKED_COUNT; i++)
			html = html.replace("%checked" + i + "%", getChecked(i, classesSortType));

		html = BbsUtil.htmlAll(html, player);
		ShowBoard.separateAndSend(html, player);
	}

	/**
	 * The "Characters" listing for bbs_partymatching => the "Classes" button calls
	 * `_bbsPartymatching`.
	 */
	private String getCharactersPartymatching(Player visitor, int charSort, int asc,
			int classSort, int page, int charToView) {
		List<Player> allPlayers = getPlayerList(visitor, charSort, asc, classSort);
		int badCharacters = 0;
		boolean nextPageOk = true;
		String html = "";

		for (int i = MAX_PER_PAGE * page; i < (MAX_PER_PAGE + badCharacters + page * MAX_PER_PAGE); i++) {
			if (i >= allPlayers.size()) {
				nextPageOk = false;
				break;
			}
			Player p = allPlayers.get(i);

			// Filter
			if (!isClassTestPassed(p, classSort)) {
				badCharacters++;
				continue;
			}

			int maxLevel = getMaxLevel(p, classSort);
			int unlocksSize = getUnlocksSize(p, classSort);

			html += "<table bgcolor=" + getLineColor((charToView == p.getObjectId()) ? 2 : 0, i)
					+ " width=425 border=0 cellpadding=0 cellspacing=0><tr>";
			html += "<td width=200><center><font color=cacd81>" + p.getName() + "</font></center></td>";
			html += "<td width=80><center><font color=cacd81>" + maxLevel + "</font></center></td>";
			html += "<td width=70><center><font color=cacd81>" + unlocksSize + "</font></center></td>";

			// "Classes" button => calls _bbsPartymatching instead of _bbslink
			html += "<td width=75>"
					+ "<button value=\"Classes\" action=\"bypass _bbsPartymatching_%class%_%sort%_%asc%_%page%_"
					+ p.getObjectId()
					+ "_0\" width=80 height=18 back=\"L2UI_CT1.Button_DF_Down\" fore=\"L2UI_ct1.button_df\">"
					+ "</td>";

			html += "</tr></table>";
		}

		// Pagination
		html += "<center><table><tr>";
		if (page > 0) {
			html += "<td><button value=\"Prev\" action=\"bypass _bbsPartymatching_%class%_%sort%_%asc%_"
					+ (page - 1)
					+ "_%char%_%classPage%\" width=80 height=18 back=\"L2UI_CT1.Button_DF_Down\" "
					+ "fore=\"L2UI_ct1.button_df\"></td>";
		}
		if (nextPageOk) {
			html += "<td><button value=\"Next\" action=\"bypass _bbsPartymatching_%class%_%sort%_%asc%_"
					+ (page + 1)
					+ "_%char%_%classPage%\" width=80 height=18 back=\"L2UI_CT1.Button_DF_Down\" "
					+ "fore=\"L2UI_ct1.button_df\"></td>";
		}
		html += "</tr></table></center>";

		return html;
	}

	/**
	 * The "Classes" list on the right side for bbs_partymatching => the "Invite"
	 * button calls _bbsPartymatching
	 */
	private String getClassesPartymatching(int charObjId, int page) {
		if (charObjId == 0)
			return "";

		Player playerToView = GameObjectsStorage.getPlayer(charObjId);
		if (playerToView == null)
			return "";

		String html = "";
		int i = 0;
		boolean nextPageOk = false;

		for (Object obj : playerToView.getUnlocks().getAllUnlocks().getValues()) {
			UnlockedClass clazz = (UnlockedClass) obj;
			if (ClassId.values()[clazz.getId()].getLevel() < 4)
				continue;
			if (ClassId.values()[clazz.getId()].getRace() == Race.kamael)
				continue;

			if (i < MAX_PER_PAGE * page) {
				i++;
				continue;
			}
			if (i >= (MAX_PER_PAGE + page * MAX_PER_PAGE)) {
				nextPageOk = true;
				break;
			}

			int type;
			if (clazz.getId() == playerToView.getActiveClassClassId().getId())
				type = 2;
			else if (clazz.getId() == playerToView.getSecondaryClassId())
				type = 1;
			else
				type = 0;

			html += "<table bgcolor=" + getLineColor(type, i) + " width=320 border=0 cellpadding=0 cellspacing=0>";
			html += "<tr>";
			html += "<td width=150><center><font color=cacd81>"
					+ getFullClassName(ClassId.values()[clazz.getId()]) + "</font></center></td>";
			html += "<td width=80><center><font color=cacd81>" + clazz.getLevel() + "</font></center></td>";

			// "Invite" => calls _bbsPartymatching
			html += "<td width=90>"
					+ "<button value=\"Invite\" action=\"bypass _bbsPartymatching_%class%_%sort%_%asc%_%page%_%char%_%classPage%_"
					+ clazz.getId()
					+ "\" width=80 height=18 back=\"L2UI_CT1.Button_DF_Down\" fore=\"L2UI_ct1.button_df\">"
					+ "</td>";
			html += "</tr></table>";
			i++;
		}

		// Pagination
		html += "<center><table width=320 border=0 cellpadding=0 cellspacing=0><tr>";
		if (page > 0) {
			html += "<td><center>"
					+ "<button value=\"Prev\" action=\"bypass _bbsPartymatching_%class%_%sort%_%asc%_%page%_%char%_"
					+ (page - 1)
					+ "\" width=80 height=18 back=\"L2UI_CT1.Button_DF_Down\" fore=\"L2UI_ct1.button_df\">"
					+ "</center></td>";
		}
		if (nextPageOk) {
			html += "<td><center>"
					+ "<button value=\"Next\" action=\"bypass _bbsPartymatching_%class%_%sort%_%asc%_%page%_%char%_"
					+ (page + 1)
					+ "\" width=80 height=18 back=\"L2UI_CT1.Button_DF_Down\" fore=\"L2UI_ct1.button_df\">"
					+ "</center></td>";
		}
		html += "</tr></table></center>";

		return html;
	}

	// -------------------------------------------------------------------------
	// Common placeholders & utilities
	// -------------------------------------------------------------------------
	private String replaceCommon(String text, int classesSortType, int sortType,
			int asc, int page, int charObjId, int classPage) {
		text = text.replace("%class%", String.valueOf(classesSortType));
		text = text.replace("%sort%", String.valueOf(sortType));
		text = text.replace("%asc%", String.valueOf(asc));
		text = text.replace("%asc2%", String.valueOf(asc == 0 ? 1 : 0));
		text = text.replace("%page%", String.valueOf(page));
		text = text.replace("%char%", String.valueOf(charObjId));
		text = text.replace("%classPage%", String.valueOf(classPage));
		return text;
	}

	/**
	 * Determines if a given Player can be invited.
	 */
	private static String canJoinParty(Player p) {
		if (p.isGM())
			return "Don't invite GMs...";
		if (p.getParty() != null)
			return "This character already found a party!";
		if (p.isInOfflineMode())
			return "This character is offline!";
		if (p.isInOlympiadMode())
			return "This character is currently fighting in the Olympiad!";
		if (p.isInObserverMode())
			return "This character is currently observing an Olympiad Match!";
		if (p.isInCombat())
			return "This character is currently in Combat!";
		if (p.getCursedWeaponEquippedId() != 0)
			return "Players with Cursed Weapons cannot join party!";
		if (!p.isPartyMatchingVisible())
			return "Player doesnt want to join any party!";
		if (p.getPrivateStoreType() > 0)
			return "Players that have Private Store, cannot join partys!";
		return "";
	}

	/**
	 * Returns all players that pass the filter & are visible on party matching.
	 */
	private List<Player> getPlayerList(Player player, int sortType, int asc, int classSortType) {
		List<Player> allPlayers = new ArrayList<Player>();

		// "Your Party" filter => just yourself (if no party) or entire party
		if (classSortType == 8) {
			if (player.getParty() == null)
				allPlayers.add(player);
			else
				allPlayers.addAll(player.getParty().getPartyMembers());

			Collections.sort(allPlayers, new CharComparator(sortType, classSortType, asc));
			return allPlayers;
		}

		// Otherwise => all visible players
		for (Player singlePlayer : GameObjectsStorage.getAllPlayersForIterate()) {
			if (canJoinParty(singlePlayer).isEmpty()) {
				// Additional check: must match chosen filter
				if (!isClassTestPassed(singlePlayer, classSortType))
					continue;
				allPlayers.add(singlePlayer);
			}
		}

		Collections.sort(allPlayers, new CharComparator(sortType, classSortType, asc));
		return allPlayers;
	}

	private class CharComparator implements Comparator<Player> {
		private final int _type;
		private final int _classType;
		private final int _asc;

		private CharComparator(int sortType, int classType, int asc) {
			_type = sortType;
			_classType = classType;
			_asc = asc;
		}

		@Override
		public int compare(Player o1, Player o2) {
			if (_asc == 1) {
				Player temp = o1;
				o1 = o2;
				o2 = temp;
			}
			// 0 => name, 1 => highest lvl, 2 => # of unlocks
			if (_type == 0) // name
				return o1.getName().compareTo(o2.getName());

			if (_type == 1) // highest lvl
			{
				int lv1 = getMaxLevel(o1, _classType);
				int lv2 = getMaxLevel(o2, _classType);
				return Integer.compare(lv2, lv1); // descending
			}

			if (_type == 2) // # of unlocks
			{
				int un1 = getUnlocksSize(o1, _classType);
				int un2 = getUnlocksSize(o2, _classType);
				return Integer.compare(un2, un1); // descending
			}
			return 0;
		}
	}

	private int getMaxLevel(Player player, int classSortType) {
		ClassId[] group = getNeededClasses(classSortType);
		int maxLevel = 0;
		for (Object obj : player.getUnlocks().getAllUnlocks().getValues()) {
			UnlockedClass unlock = (UnlockedClass) obj;
			if (!containsClass(group, unlock.getId()))
				continue;
			int level = Experience.getLevel(unlock.getExp());
			if (level > maxLevel)
				maxLevel = level;
		}
		return maxLevel;
	}

	private int getUnlocksSize(Player player, int classSortType) {
		ClassId[] group = getNeededClasses(classSortType);
		int count = 0;
		for (Object obj : player.getUnlocks().getAllUnlocks().getValues()) {
			UnlockedClass unlock = (UnlockedClass) obj;
			if (!containsClass(group, unlock.getId()))
				continue;
			if (unlock.getId() >= 88)
				count++;
		}
		return count;
	}

	private boolean isClassTestPassed(Player player, int classSortType) {
		ClassId[] needed = getNeededClasses(classSortType);
		for (int unlockedClass : player.getUnlocks().getAllUnlocks().keys()) {
			for (ClassId c : needed) {
				if (c.getId() == unlockedClass)
					return true;
			}
		}
		return false;
	}

	private boolean containsClass(ClassId[] group, int clazz) {
		for (ClassId c : group) {
			if (c.getId() == clazz)
				return true;
		}
		return false;
	}

	/**
	 * Filter which classes are relevant for the chosen type (All, Buffers, Tanks,
	 * etc.).
	 */
	private ClassId[] getNeededClasses(int type) {
		switch (type) {
			case 0: // All
				return ClassId.values();
			case 1: // Buffers
				return new ClassId[] {
						ClassId.hierophant, ClassId.evaSaint, ClassId.shillienSaint,
						ClassId.dominator, ClassId.doomcryer
				};
			case 2: // BD
				return new ClassId[] { ClassId.spectralDancer };
			case 3: // SWS
				return new ClassId[] { ClassId.swordMuse };
			case 4: // Healers
				return new ClassId[] { ClassId.cardinal, ClassId.evaSaint, ClassId.shillienSaint };
			case 5: // Tanks
				return new ClassId[] {
						ClassId.phoenixKnight, ClassId.hellKnight,
						ClassId.evaTemplar, ClassId.shillienTemplar
				};
			case 6: // Mage DD
				return new ClassId[] {
						ClassId.archmage, ClassId.soultaker, ClassId.arcanaLord,
						ClassId.mysticMuse, ClassId.elementalMaster, ClassId.stormScreamer,
						ClassId.spectralMaster, ClassId.dominator, ClassId.doomcryer
				};
			case 7: // Fighter DD
				return new ClassId[] {
						ClassId.dreadnought, ClassId.duelist, ClassId.adventurer,
						ClassId.sagittarius, ClassId.windRider, ClassId.moonlightSentinel,
						ClassId.ghostHunter, ClassId.ghostSentinel, ClassId.titan,
						ClassId.grandKhauatari, ClassId.fortuneSeeker
				};
			case 8: // "Your Party" => handled externally
				return ClassId.values();
		}
		return ClassId.values();
	}

	private static String getFullClassName(ClassId classIndex) {
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
				String name = classIndex.name();
				return name.substring(0, 1).toUpperCase() + name.substring(1);
		}
	}

	private String getChecked(int i, int classSortType) {
		if (classSortType == i)
			return "L2UI.Checkbox_checked";
		return "L2UI.CheckBox";
	}

	private String getLineColor(int type, int i) {
		// type=2 => main/selected class, 1 => secondary
		if (type == 2)
			return "284d27";
		else if (type == 1)
			return "6a3d1d";

		// alternate row color
		if ((i % 2) == 0)
			return "18191e";
		return "22181a";
	}

	// -------------------------------------------------------------------------
	// OnAnswerListener for invites
	// -------------------------------------------------------------------------
	public static class InviteAnswer implements OnAnswerListener {
		private final Player _invited;
		private final Player _inviter;
		private final ClassId _class;

		public InviteAnswer(Player invited, Player inviter, ClassId classNeeded) {
			_invited = invited;
			_inviter = inviter;
			_class = classNeeded;
		}

		@Override
		public void sayYes() {
			String inviteMsg = canJoinParty(_invited);
			if (!inviteMsg.isEmpty()) {
				_inviter.sendMessage(inviteMsg);
				return;
			}
			// forming the party
			Party party = _inviter.getParty();
			if (party == null)
				_inviter.setParty(party = new Party(_inviter, 0));

			_invited.joinParty(party);
			_invited.sendPacket(JoinParty.SUCCESS);
			_inviter.sendPacket(JoinParty.SUCCESS);

			if (_invited.getActiveClass().isKamael())
				return;

			// if same class => ask for teleport
			if (_class == _invited.getActiveClassClassId()
					|| _class.getId() == _invited.getSecondaryClassId()) {
				askForTeleport(_invited, _inviter);
			}
			// if no other choices
			else if (getNotChosenClasses(_invited, _class).length() == 0) {
				_invited.switchStackClass(_class.getId(), true);
				_invited.sendMessage("Your primary class has been switched!");
				askForTeleport(_invited, _inviter);
			} else {
				showFirstChooseClassPage(_invited, _inviter, _class);
			}
		}

		@Override
		public void sayNo() {
			_inviter.sendMessage(_invited.getName() + " cancelled party join request!");
		}
	}

	public static class TeleportAnswer implements OnAnswerListener {
		private final Player _player;
		private final Player _inviter;

		public TeleportAnswer(Player p, Player i) {
			_player = p;
			_inviter = i;
		}

		@Override
		public void sayYes() {
			if (canBeTeleported(_player, _inviter)) {
				_player.teleToLocation(_inviter.getLoc());
				_player.addNewTask(TaskType.PartySummon, 5 * 60000);
				_inviter.addNewTask(TaskType.PartySummon, 5 * 60000);
			} else
				_player.sendMessage("I am sorry but you cannot be teleported right now!");
		}

		@Override
		public void sayNo() {
			// do nothing
		}
	}

	private static void showFirstChooseClassPage(Player player, Player inviter, ClassId classToChoose) {
		String text = "<html><title>Choose Class</title><body><br>"
				+ "<center><img src=\"L2UI_CH3.herotower_deco\" width=256 height=32><br>"
				+ "Would you like your " + getFullClassName(classToChoose) + " as your Primary or Secondary?"
				+ "<br><table><tr>"
				+ "<td><button value=\"Primary\" action=\"bypass _bbsclass_Primary_" + inviter.getObjectId() + "_"
				+ classToChoose.getId()
				+ "\" width=62 height=32 back=\"L2UI_CT1.Button_DF_Down\" fore=\"L2UI_ct1.button_df\"></td>"
				+ "<td><button value=\"Secondary\" action=\"bypass _bbsclass_Secondary_" + inviter.getObjectId() + "_"
				+ classToChoose.getId()
				+ "\" width=62 height=32 back=\"L2UI_CT1.Button_DF_Down\" fore=\"L2UI_ct1.button_df\"></td>"
				+ "</tr></table>";

		NpcHtmlMessage msg = new NpcHtmlMessage(0);
		msg.setHtml(text);
		player.sendPacket(msg);
	}

	private void showSecondChooseClassPage(Player player, int inviterObjectId,
			ClassId choosenClass, boolean primary) {
		String text = "<html><title>Choose Class</title><body><br>"
				+ "<center><img src=\"L2UI_CH3.herotower_deco\" width=256 height=32><br>"
				+ "Select your " + (primary ? "Secondary" : "Primary") + " Class:<br>"
				+ "<combobox width=130 var=\"classes\" list=" + getNotChosenClasses(player, choosenClass) + ">"
				+ "<br><button value=\"Choose\" action=\"bypass _bbsclass_secondChoise_"
				+ inviterObjectId + "_" + (primary ? 1 : 0)
				+ "_ $classes\" width=62 height=32 back=\"L2UI_CT1.Button_DF_Down\" "
				+ "fore=\"L2UI_ct1.button_df\">";

		NpcHtmlMessage msg = new NpcHtmlMessage(0);
		msg.setHtml(text);
		player.sendPacket(msg);
	}

	private static void askForTeleport(Player player, Player inviter) {
		if (canBeTeleported(player, inviter)) {
			ConfirmDlg packet = new ConfirmDlg(SystemMsg.S1, 60000)
					.addString("Do you want to be teleported to " + inviter.getName() + "?");
			player.ask(packet, new TeleportAnswer(player, inviter));
		}
	}

	private static boolean canBeTeleported(Player p, Player i) {
		if (p == null || i == null)
			return false;
		if (p.getParty() == null)
			return false;
		if (p.equals(i))
			return false;
		if (p.isAlikeDead())
			return false;
		return canDoTeleport(p) && canDoTeleport(i);
	}

	private static boolean canDoTeleport(Player p) {
		if (p.isInZone(ZoneType.no_escape) || p.isInZone(ZoneType.no_landing)
				|| p.isInZone(ZoneType.SIEGE) || p.isInZoneBattle()
				|| p.isInZone(ZoneType.no_summon))
			return false;

		if (p.getActiveClassClassId().getLevel() < 2)
			return false;

		if (p.isInOlympiadMode() || p.isInObserverMode()
				|| p.isFlying() || p.isFestivalParticipant()
				|| p.getTournamentMatch() != null)
			return false;

		if (p.isInBoat() || p.getReflection() != ReflectionManager.DEFAULT)
			return false;

		if (p.taskExists(TaskType.PartySummon))
			return false;
		return true;
	}

	/**
	 * Build a semicolon-delimited list of "not chosen classes" for final switching,
	 * used in stackclass logic.
	 */
	private static String getNotChosenClasses(Player player, ClassId choosenClass) {
		String classes = "";
		for (Object obj : player.getUnlocks().getAllUnlocks().getValues()) {
			UnlockedClass ul = (UnlockedClass) obj;
			ClassId cid = ClassId.values()[ul.getId()];

			if (cid.getLevel() < 4 || cid == choosenClass)
				continue;
			// allow dwarves or same race
			if (cid.getRace() == choosenClass.getRace()
					|| choosenClass.getRace() == Race.dwarf
					|| cid.getRace() == Race.dwarf) {
				classes += ALL_CLASS_SHORT_NAMES[cid.getId() - 88] + ";";
			}
		}
		if (classes.length() > 0)
			classes = classes.substring(0, classes.length() - 1);
		return classes;
	}

	@Override
	public void onWriteCommand(Player player, String bypass,
			String arg1, String arg2,
			String arg3, String arg4,
			String arg5) {
		// not used
	}

	@Override
	public void onLoad() {
		_log.info("HomePageCommunity onLoad() called.");
		CommunityBoardManager.getInstance().registerHandler(this);
	}

	@Override
	public void onReload() {
		_log.info("HomePageCommunity onReload() called.");
		CommunityBoardManager.getInstance().removeHandler(this);
	}

	@Override
	public void onShutdown() {
		_log.info("HomePageCommunity onShutdown() called.");
	}
}
