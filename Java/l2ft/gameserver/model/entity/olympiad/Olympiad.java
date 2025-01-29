// C:\l2sq\Pac Project\Java\l2ft\gameserver\model\entity\olympiad\Olympiad.java
package l2ft.gameserver.model.entity.olympiad;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

import l2ft.commons.configuration.ExProperties;
import l2ft.gameserver.Config;
import l2ft.gameserver.ThreadPoolManager;
import l2ft.gameserver.dao.OlympiadNobleDAO;
import l2ft.gameserver.instancemanager.OlympiadHistoryManager;
import l2ft.gameserver.instancemanager.ServerVariables;
import l2ft.gameserver.model.GameObjectsStorage;
import l2ft.gameserver.model.Party;
import l2ft.gameserver.model.Player;
import l2ft.gameserver.model.base.ClassId;
import l2ft.gameserver.model.entity.Hero;
import l2ft.gameserver.model.instances.NpcInstance;
import l2ft.gameserver.network.l2.components.CustomMessage;
import l2ft.gameserver.network.l2.components.SystemMsg;
import l2ft.gameserver.network.l2.s2c.SystemMessage2;
import l2ft.gameserver.templates.StatsSet;
import l2ft.gameserver.utils.Location;
import l2ft.gameserver.utils.MultiValueIntegerMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Olympiad {
	private static final Logger _log = LoggerFactory.getLogger(Olympiad.class);

	public static Map<Integer, StatsSet> _nobles;
	public static Map<Integer, Integer> _noblesRank;
	public static List<StatsSet> _heroesToBe;
	public static MultiValueIntegerMap _team2Registers = new MultiValueIntegerMap();
	public static MultiValueIntegerMap _team4Registers = new MultiValueIntegerMap();
	public static MultiValueIntegerMap _team6Registers = new MultiValueIntegerMap();

	// public static final int DEFAULT_POINTS = 50;
	// private static final int WEEKLY_POINTS = 10;

	public static final String OLYMPIAD_HTML_PATH = "olympiad/";

	public static final String CHAR_ID = "char_id";
	public static final String CLASS_COMBINATION = "class_combination";
	public static final String CHAR_NAME = "char_name";
	public static final String POINTS = "olympiad_points";
	public static final String POINTS_PAST = "olympiad_points_past";
	public static final String POINTS_PAST_STATIC = "olympiad_points_past_static";
	public static final String COMP_DONE = "competitions_done";
	public static final String COMP_WIN = "competitions_win";
	public static final String COMP_LOOSE = "competitions_loose";
	public static final String GAME_TEAM2_COUNT = "game_team2_count";
	public static final String GAME_TEAM4_COUNT = "game_team4_count";
	public static final String GAME_TEAM6_COUNT = "game_team6_count";

	public static long _olympiadEnd;
	public static long _validationEnd;
	public static int _period;
	public static long _nextWeeklyChange;
	public static int _currentCycle;
	private static long _compEnd;
	private static Calendar _compStart;
	public static boolean _inCompPeriod;
	public static boolean _isOlympiadEnd;

	private static ScheduledFuture<?> _scheduledOlympiadEnd;
	public static ScheduledFuture<?> _scheduledManagerTask;
	public static ScheduledFuture<?> _scheduledWeeklyTask;
	public static ScheduledFuture<?> _scheduledValdationTask;

	public static final Stadia[] STADIUMS = new Stadia[Config.OLYMPIAD_STADIAS_COUNT];

	public static OlympiadManager _manager;
	private static List<NpcInstance> _npcs = new ArrayList<NpcInstance>();

	public static void load() {
		_nobles = new ConcurrentHashMap<Integer, StatsSet>();
		_currentCycle = ServerVariables.getInt("Olympiad_CurrentCycle", -1);
		_period = ServerVariables.getInt("Olympiad_Period", -1);
		_olympiadEnd = ServerVariables.getLong("Olympiad_End", -1);
		_validationEnd = ServerVariables.getLong("Olympiad_ValdationEnd", -1);
		_nextWeeklyChange = ServerVariables.getLong("Olympiad_NextWeeklyChange", -1);

		ExProperties olympiadProperties = Config.load(Config.OLYMPIAD_DATA_FILE);

		if (_currentCycle == -1)
			_currentCycle = olympiadProperties.getProperty("CurrentCycle", 1);
		if (_period == -1)
			_period = olympiadProperties.getProperty("Period", 0);
		if (_olympiadEnd == -1)
			_olympiadEnd = olympiadProperties.getProperty("OlympiadEnd", 0L);
		if (_validationEnd == -1)
			_validationEnd = olympiadProperties.getProperty("ValdationEnd", 0L);
		if (_nextWeeklyChange == -1)
			_nextWeeklyChange = olympiadProperties.getProperty("NextWeeklyChange", 0L);

		initStadiums();

		OlympiadHistoryManager.getInstance();
		OlympiadNobleDAO.getInstance().select();
		OlympiadDatabase.loadNoblesRank();

		switch (_period) {
			case 0:
				if (_olympiadEnd == 0 || _olympiadEnd < Calendar.getInstance().getTimeInMillis())
					OlympiadDatabase.setNewOlympiadEnd();
				else
					_isOlympiadEnd = false;
				break;
			case 1:
				_isOlympiadEnd = true;
				_scheduledValdationTask = ThreadPoolManager.getInstance().schedule(new ValidationTask(),
						getMillisToValidationEnd());
				break;
			default:
				_log.warn("Olympiad System: Omg something went wrong in loading!! Period = " + _period);
				return;
		}

		_log.info("Olympiad System: Loading Olympiad System....");
		if (_period == 0)
			_log.info("Olympiad System: Currently in Olympiad Period");
		else
			_log.info("Olympiad System: Currently in Validation Period");

		_log.info("Olympiad System: Period Ends....");

		long milliToEnd;
		if (_period == 0)
			milliToEnd = getMillisToOlympiadEnd();
		else
			milliToEnd = getMillisToValidationEnd();

		double numSecs = milliToEnd / 1000 % 60;
		double countDown = (milliToEnd / 1000 - numSecs) / 60;
		int numMins = (int) Math.floor(countDown % 60);
		countDown = (countDown - numMins) / 60;
		int numHours = (int) Math.floor(countDown % 24);
		int numDays = (int) Math.floor((countDown - numHours) / 24);

		_log.info("Olympiad System: In " + numDays + " days, " + numHours + " hours and " + numMins + " mins.");

		if (_period == 0) {
			_log.info("Olympiad System: Next Weekly Change is in....");

			milliToEnd = getMillisToWeekChange();

			double numSecs2 = milliToEnd / 1000 % 60;
			double countDown2 = (milliToEnd / 1000 - numSecs2) / 60;
			int numMins2 = (int) Math.floor(countDown2 % 60);
			countDown2 = (countDown2 - numMins2) / 60;
			int numHours2 = (int) Math.floor(countDown2 % 24);
			int numDays2 = (int) Math.floor((countDown2 - numHours2) / 24);

			_log.info("Olympiad System: In " + numDays2 + " days, " + numHours2 + " hours and " + numMins2 + " mins.");
		}

		_log.info("Olympiad System: Loaded " + _nobles.size() + " Noblesses");

		if (_period == 0)
			init();
	}

	private static void initStadiums() {
		for (int i = 0; i < STADIUMS.length; i++)
			if (STADIUMS[i] == null)
				STADIUMS[i] = new Stadia();
	}

	public static void init() {
		if (_period == 1)
			return;

		_compStart = Calendar.getInstance();
		_compStart.set(Calendar.HOUR_OF_DAY, Config.ALT_OLY_START_TIME);
		_compStart.set(Calendar.MINUTE, Config.ALT_OLY_MIN);
		_compEnd = _compStart.getTimeInMillis() + Config.ALT_OLY_CPERIOD;

		if (_scheduledOlympiadEnd != null)
			_scheduledOlympiadEnd.cancel(false);
		_scheduledOlympiadEnd = ThreadPoolManager.getInstance().schedule(new OlympiadEndTask(),
				getMillisToOlympiadEnd());

		updateCompStatus();

		if (_scheduledWeeklyTask != null)
			_scheduledWeeklyTask.cancel(false);
		_scheduledWeeklyTask = ThreadPoolManager.getInstance().scheduleAtFixedRate(new WeeklyTask(),
				getMillisToWeekChange(), Config.ALT_OLY_WPERIOD);
	}

	public static synchronized boolean registerNoble(Player noble, CompType type) {
		if (noble.getActiveClassClassId().getLevel() != 4) {
			return false;
		}

		if (!_inCompPeriod || _isOlympiadEnd) {
			noble.sendPacket(SystemMsg.THE_GRAND_OLYMPIAD_GAMES_ARE_NOT_CURRENTLY_IN_PROGRESS);
			return false;
		}

		if (getMillisToOlympiadEnd() <= 600 * 1000) {
			noble.sendPacket(SystemMsg.THE_GRAND_OLYMPIAD_GAMES_ARE_NOT_CURRENTLY_IN_PROGRESS);
			return false;
		}

		if (getMillisToCompEnd() <= 600 * 1000) {
			noble.sendPacket(SystemMsg.THE_GRAND_OLYMPIAD_GAMES_ARE_NOT_CURRENTLY_IN_PROGRESS);
			return false;
		}

		if (noble.isCursedWeaponEquipped()) {
			noble.sendPacket(SystemMsg.YOU_CANNOT_REGISTER_WHILE_IN_POSSESSION_OF_A_CURSED_WEAPON);
			return false;
		}

		if (!validPlayer(noble, noble, type)) {
			return false;
		}

		if (getNoblePoints(noble.getObjectId(), getClassCombination(noble)) < 3) {
			noble.sendMessage(new CustomMessage("l2ft.gameserver.model.entity.Olympiad.LessPoints", noble));
			return false;
		}

		if (noble.getOlympiadGame() != null) {
			//
			return false;
		}

		Party party = noble.getParty();
		if (party == null) {
			noble.sendPacket(SystemMsg.ONLY_A_PARTY_LEADER_CAN_REQUEST_A_TEAM_MATCH);
			return false;
		}

		if (party.getMemberCount() != type.getTeamSize()) {
			noble.sendMessage("There need to be " + type.getTeamSize() + " members in party!");
			return false;
		}

		for (Player member : party.getPartyMembers()) {
			if (!validPlayer(noble, member, type)) {
				return false;
			}
		}

		switch (type) {
			case TEAM2: {
				_team2Registers.putAll(noble.getObjectId(), party.getPartyMembersObjIds());
				noble.sendMessage("You have been registered for the Grand Olympiad Waiting List for 2vs2 Team Match!");
				break;
			}
			case TEAM4: {
				_team4Registers.putAll(noble.getObjectId(), party.getPartyMembersObjIds());
				noble.sendMessage("You have been registered for the Grand Olympiad Waiting List for 4vs4 Team Match!");
				break;
			}
			case TEAM6: {
				_team6Registers.putAll(noble.getObjectId(), party.getPartyMembersObjIds());
				noble.sendMessage("You have been registered for the Grand Olympiad Waiting List for 6vs6 Team Match!");
				break;
			}
		}

		return true;
	}

	private static boolean validPlayer(Player sendPlayer, Player validPlayer, CompType type) {
		if (!validPlayer.isNoble()) {
			sendPlayer.sendPacket(new SystemMessage2(
					SystemMsg.C1_DOES_NOT_MEET_THE_PARTICIPATION_REQUIREMENTS_ONLY_NOBLESSE_CHARACTERS_CAN_PARTICIPATE_IN_THE_OLYMPIAD)
					.addName(validPlayer));
			return false;
		}

		int[] ar = getWeekGameCounts(validPlayer.getObjectId());

		switch (type) {
			case TEAM2:
				if (_team2Registers.containsValue(validPlayer.getObjectId())) {
					sendPlayer.sendMessage("You are already registered on 2vs2 team match!");
					return false;
				}

				if (ar[1] == 0) {
					validPlayer.sendMessage("You cannot more times in 2vs2 Team Match in this week!");
					return false;
				}
				break;
			case TEAM4:
				if (_team4Registers.containsValue(validPlayer.getObjectId())) {
					sendPlayer.sendMessage("You are already registered on 4vs4 team match!");
					return false;
				}
				if (ar[2] == 0) {
					validPlayer.sendMessage("You cannot more times in 4vs4 Team Match in this week!");
					return false;
				}
				break;
			case TEAM6:
				if (_team6Registers.containsValue(validPlayer.getObjectId())) {
					sendPlayer.sendMessage("You are already registered on 6vs6 team match!");
					return false;
				}
				if (ar[3] == 0) {
					validPlayer.sendMessage("You cannot more times in 6vs6 Team Match in this week!");
					return false;
				}
				break;
		}

		if (ar[0] == 0) {
			validPlayer.sendPacket(SystemMsg.THE_MAXIMUM_MATCHES_YOU_CAN_PARTICIPATE_IN_1_WEEK_IS_70);
			return false;
		}

		if (isRegisteredInComp(validPlayer)) {
			sendPlayer.sendPacket(new SystemMessage2(SystemMsg.C1_IS_ALREADY_REGISTERED_ON_THE_MATCH_WAITING_LIST)
					.addName(validPlayer));
			return false;
		}

		return true;
	}

	public static synchronized void logoutPlayer(Player player) {
		MultiValueIntegerMap[] maps = { _team2Registers, _team4Registers, _team6Registers };
		boolean found = false;
		for (MultiValueIntegerMap map : maps) {
			for (Entry<Integer, List<Integer>> list : map.entrySet()) {
				for (Integer i : list.getValue())
					if (i.equals(player.getObjectId())) {
						map.remove(list.getKey());
						found = true;
						break;
					}
				if (found)
					break;
			}
			if (found)
				break;
		}

		if (player.isInOlympiadMode()) {
			OlympiadGame game = player.getOlympiadGame();
			if (game != null)
				try {
					if (!game.logoutPlayer(player) && !game.validated)
						game.endGame(20000, true);
				} catch (Exception e) {
					_log.error("", e);
				}
		}
	}

	public static synchronized boolean unRegisterNoble(Player noble) {
		if (!_inCompPeriod || _isOlympiadEnd) {
			noble.sendPacket(SystemMsg.THE_GRAND_OLYMPIAD_GAMES_ARE_NOT_CURRENTLY_IN_PROGRESS);
			return false;
		}

		if (!noble.isNoble()) {
			noble.sendPacket(SystemMsg.THE_GRAND_OLYMPIAD_GAMES_ARE_NOT_CURRENTLY_IN_PROGRESS);
			return false;
		}

		if (!isRegistered(noble)) {
			noble.sendPacket(SystemMsg.YOU_ARE_NOT_CURRENTLY_REGISTERED_FOR_THE_GRAND_OLYMPIAD);
			return false;
		}

		if (noble.getParty() != null && !noble.getParty().getPartyLeader().equals(noble)) {
			noble.sendMessage("Only Party leader may unregister!");
			return false;
		}

		OlympiadGame game = noble.getOlympiadGame();
		if (game != null) {
			if (game.getStatus() == BattleStatus.Begin_Countdown) {
				// TODO: System Message
				// TODO [VISTALL] Ń�Đ·Đ˝Đ°Ń‚ŃŚ Đ»Đ¸ ĐżŃ€ĐµŃ€Ń‹Đ˛Đ°ĐµŃ‚Ń�ŃŹ Đ±ĐľĐą Đ¸ ĐµŃ�Đ»Đ¸
				// Ń‚Đ°Đş Đ»Đ¸ ŃŤŃ‚Đľ Ń‚Đ° ĐĽĐµŃ�Ń�Đ°ĐłĐ°
				// SystemMsg.YOUR_OPPONENT_MADE_HASTE_WITH_THEIR_TAIL_BETWEEN_THEIR_LEGS_THE_MATCH_HAS_BEEN_CANCELLED
				noble.sendMessage("Now you can't cancel participation in the Grand Olympiad.");
				return false;
			}

			try {
				if (!game.logoutPlayer(noble) && !game.validated)
					game.endGame(20000, true);
			} catch (Exception e) {
				_log.error("", e);
			}
		}
		_team2Registers.removeValue(noble.getObjectId());
		_team4Registers.remove(new Integer(noble.getObjectId()));
		_team6Registers.removeValue(noble.getObjectId());

		noble.sendPacket(SystemMsg.YOU_HAVE_BEEN_REMOVED_FROM_THE_GRAND_OLYMPIAD_WAITING_LIST);

		return true;
	}

	private static synchronized void updateCompStatus() {
		long milliToStart = getMillisToCompBegin();
		double numSecs = milliToStart / 1000 % 60;
		double countDown = (milliToStart / 1000 - numSecs) / 60;
		int numMins = (int) Math.floor(countDown % 60);
		countDown = (countDown - numMins) / 60;
		int numHours = (int) Math.floor(countDown % 24);
		int numDays = (int) Math.floor((countDown - numHours) / 24);

		_log.info("Olympiad System: Competition Period Starts in " + numDays + " days, " + numHours + " hours and "
				+ numMins + " mins.");
		_log.info("Olympiad System: Event starts/started: " + _compStart.getTime());

		ThreadPoolManager.getInstance().schedule(new CompStartTask(), getMillisToCompBegin());
	}

	private static long getMillisToOlympiadEnd() {
		return _olympiadEnd - System.currentTimeMillis();
	}

	static long getMillisToValidationEnd() {
		if (_validationEnd > System.currentTimeMillis())
			return _validationEnd - System.currentTimeMillis();
		return 10L;
	}

	public static boolean isOlympiadEnd() {
		return _isOlympiadEnd;
	}

	public static boolean inCompPeriod() {
		return _inCompPeriod;
	}

	private static long getMillisToCompBegin() {
		if (_compStart.getTimeInMillis() < Calendar.getInstance().getTimeInMillis()
				&& _compEnd > Calendar.getInstance().getTimeInMillis())
			return 10L;
		if (_compStart.getTimeInMillis() > Calendar.getInstance().getTimeInMillis())
			return _compStart.getTimeInMillis() - Calendar.getInstance().getTimeInMillis();
		return setNewCompBegin();
	}

	private static long setNewCompBegin() {
		_compStart = Calendar.getInstance();
		_compStart.set(Calendar.HOUR_OF_DAY, Config.ALT_OLY_START_TIME);
		_compStart.set(Calendar.MINUTE, Config.ALT_OLY_MIN);
		_compStart.add(Calendar.HOUR_OF_DAY, 24);
		_compEnd = _compStart.getTimeInMillis() + Config.ALT_OLY_CPERIOD;

		_log.info("Olympiad System: New Schedule @ " + _compStart.getTime());

		return _compStart.getTimeInMillis() - Calendar.getInstance().getTimeInMillis();
	}

	public static long getMillisToCompEnd() {
		return _compEnd - Calendar.getInstance().getTimeInMillis();
	}

	private static long getMillisToWeekChange() {
		if (_nextWeeklyChange > Calendar.getInstance().getTimeInMillis())
			return _nextWeeklyChange - Calendar.getInstance().getTimeInMillis();
		return 10L;
	}

	public static synchronized void doWeekTasks() {
		if (_period == 1)
			return;
		for (Map.Entry<Integer, StatsSet> entry : _nobles.entrySet()) {
			StatsSet set = entry.getValue();
			Player player = GameObjectsStorage.getPlayer(entry.getKey());

			if (_period != 1) {
				for (Entry<String, Object> stats : set.entrySet())
					if (stats.getKey().startsWith(POINTS) && !stats.getKey()
							.substring(Olympiad.POINTS.length(), Olympiad.POINTS.length() + 1).equals("_")) {
						set.set(stats.getKey(), set.getInteger(stats.getKey()) + Config.OLYMPIAD_POINTS_WEEKLY);
						OlympiadNobleDAO.getInstance().replacePoints(entry.getKey(),
								Integer.parseInt(stats.getKey().substring(POINTS.length())));
					}
			}
			set.set(GAME_TEAM2_COUNT, 0);
			set.set(GAME_TEAM4_COUNT, 0);
			set.set(GAME_TEAM6_COUNT, 0);

			if (player != null)
				player.sendPacket(new SystemMessage2(SystemMsg.C1_HAS_EARNED_S2_POINTS_IN_THE_GRAND_OLYMPIAD_GAMES)
						.addName(player).addInteger(Config.OLYMPIAD_POINTS_WEEKLY));
		}
	}

	public static int getCurrentCycle() {
		return _currentCycle;
	}

	public static synchronized void addSpectator(int id, Player spectator) {
		if (spectator.getOlympiadGame() != null || isRegistered(spectator) || Olympiad.isRegisteredInComp(spectator)) {
			spectator.sendPacket(
					SystemMsg.YOU_MAY_NOT_OBSERVE_A_GRAND_OLYMPIAD_GAMES_MATCH_WHILE_YOU_ARE_ON_THE_WAITING_LIST);
			return;
		}

		if (_manager == null || _manager.getOlympiadInstance(id) == null
				|| _manager.getOlympiadInstance(id).getStatus() == BattleStatus.Begining
				|| _manager.getOlympiadInstance(id).getStatus() == BattleStatus.Begin_Countdown) {
			spectator.sendPacket(SystemMsg.THE_GRAND_OLYMPIAD_GAMES_ARE_NOT_CURRENTLY_IN_PROGRESS);
			return;
		}

		if (spectator.getPet() != null)
			spectator.getPet().unSummon();

		OlympiadGame game = getOlympiadGame(id);
		List<Location> spawns = game.getReflection().getInstancedZone().getTeleportCoords();
		if (spawns.size() < 3) {
			Location c1 = spawns.get(0);
			Location c2 = spawns.get(1);
			spectator.enterOlympiadObserverMode(new Location((c1.x + c2.x) / 2, (c1.y + c2.y) / 2, (c1.z + c2.z) / 2),
					game, game.getReflection());
		} else
			spectator.enterOlympiadObserverMode(spawns.get(2), game, game.getReflection());
	}

	public static synchronized void removeSpectator(int id, Player spectator) {
		if (_manager == null || _manager.getOlympiadInstance(id) == null)
			return;

		_manager.getOlympiadInstance(id).removeSpectator(spectator);
	}

	public static List<Player> getSpectators(int id) {
		if (_manager == null || _manager.getOlympiadInstance(id) == null)
			return null;
		return _manager.getOlympiadInstance(id).getSpectators();
	}

	public static OlympiadGame getOlympiadGame(int gameId) {
		if (_manager == null || gameId < 0)
			return null;
		return _manager.getOlympiadGames().get(gameId);
	}

	public static int getClassCombination(Player player) {
		return getClassCombination(player.getFirstClassId(), player.getSecondaryClassId());
	}

	public static int getClassCombination(int firstClass, int secondClass) {
		int[] classes = { firstClass, secondClass };
		if (classes[0] < classes[1]) {
			classes[0] = secondClass;
			classes[1] = firstClass;
		}

		return ((classes[0] << 16) + classes[1]);
	}

	public static int[] getClassesFromCombination(int combination) {
		int[] classes = new int[2];
		classes[0] = 0x0000FFFF & combination;
		classes[1] = combination >> 16;
		return classes;
	}

	public static synchronized int getNoblessePasses(Player player) {
		int objId = player.getObjectId();

		StatsSet noble = _nobles.get(objId);
		if (noble == null)
			return 0;

		int points = noble.getInteger(POINTS_PAST);
		if (points == 0) // ĐŁĐ¶Đµ ĐżĐľĐ»Ń�Ń‡Đ¸Đ» Đ±ĐľĐ˝Ń�Ń�
			return 0;

		int rank = _noblesRank.get(objId);
		switch (rank) {
			case 1:
				points = Config.ALT_OLY_RANK1_POINTS;
				break;
			case 2:
				points = Config.ALT_OLY_RANK2_POINTS;
				break;
			case 3:
				points = Config.ALT_OLY_RANK3_POINTS;
				break;
			case 4:
				points = Config.ALT_OLY_RANK4_POINTS;
				break;
			default:
				points = Config.ALT_OLY_RANK5_POINTS;
		}

		if (player.isHero() || Hero.getInstance().isInactiveHero(player.getObjectId()))
			points += Config.ALT_OLY_HERO_POINTS;

		noble.set(POINTS_PAST, 0);
		OlympiadDatabase.saveNobleData(objId);

		return points * Config.ALT_OLY_GP_PER_POINT;
	}

	public static synchronized boolean isRegistered(Player noble) {
		if (_team2Registers.containsValue(noble.getObjectId()))
			return true;
		if (_team4Registers.containsValue(noble.getObjectId()))
			return true;
		if (_team6Registers.containsValue(noble.getObjectId()))
			return true;
		return false;
	}

	public static synchronized boolean isRegisteredInComp(Player player) {
		if (isRegistered(player))
			return true;
		if (_manager == null || _manager.getOlympiadGames() == null)
			return false;
		for (OlympiadGame g : _manager.getOlympiadGames().values())
			if (g != null && g.isRegistered(player.getObjectId()))
				return true;
		return false;
	}

	/**
	 * Đ’ĐľĐ·Đ˛Ń€Đ°Ń‰Đ°ĐµŃ‚ ĐľĐ»Đ¸ĐĽĐżĐ¸ĐąŃ�ĐşĐ¸Đµ ĐľŃ‡ĐşĐ¸ Đ·Đ° Ń‚ĐµĐşŃ�Ń‰Đ¸Đą
	 * ĐżĐµŃ€Đ¸ĐľĐ´
	 * 
	 * @param objId
	 * @return
	 */
	public static synchronized int getNoblePoints(int objId, int combination) {
		StatsSet noble = _nobles.get(objId);
		if (noble == null)
			return 0;
		return noble.getInteger(POINTS + combination, Config.OLYMPIAD_POINTS_DEFAULT);
	}

	/**
	 * Đ’ĐľĐ·Đ˛Ń€Đ°Ń‰Đ°ĐµŃ‚ ĐľĐ»Đ¸ĐĽĐżĐ¸ĐąŃ�ĐşĐ¸Đµ ĐľŃ‡ĐşĐ¸ Đ·Đ° ĐżŃ€ĐľŃ�Đ»Ń‹Đą
	 * ĐżĐµŃ€Đ¸ĐľĐ´
	 * 
	 * @param objId
	 * @return
	 */
	public static synchronized int getNoblePointsPast(int objId, int combination) {
		StatsSet noble = _nobles.get(objId);
		if (noble == null)
			return 0;
		return noble.getInteger(POINTS_PAST + combination, 0);
	}

	public static synchronized int getCompetitionDone(int objId, int combination) {
		StatsSet noble = _nobles.get(objId);
		if (noble == null)
			return 0;
		return noble.getInteger(COMP_DONE + combination, 0);
	}

	public static synchronized int getCompetitionWin(int objId, int combination) {
		StatsSet noble = _nobles.get(objId);
		if (noble == null)
			return 0;
		return noble.getInteger(COMP_WIN + combination, 0);
	}

	public static synchronized int getCompetitionLoose(int objId, int combination) {
		StatsSet noble = _nobles.get(objId);
		if (noble == null)
			return 0;
		return noble.getInteger(COMP_LOOSE + combination, 0);
	}

	public static synchronized int[] getWeekGameCounts(int objId) {
		int[] ar = new int[4];

		StatsSet noble = _nobles.get(objId);
		if (noble == null)
			return ar;

		ar[0] = Config.GAME_MAX_LIMIT - noble.getInteger(GAME_TEAM2_COUNT) - noble.getInteger(GAME_TEAM4_COUNT)
				- noble.getInteger(GAME_TEAM6_COUNT);
		ar[1] = Config.GAME_CLASSES_COUNT_LIMIT - noble.getInteger(GAME_TEAM2_COUNT);
		ar[2] = Config.GAME_NOCLASSES_COUNT_LIMIT - noble.getInteger(GAME_TEAM4_COUNT);
		ar[3] = Config.GAME_TEAM_COUNT_LIMIT - noble.getInteger(GAME_TEAM6_COUNT);

		return ar;
	}

	public static Stadia[] getStadiums() {
		return STADIUMS;
	}

	public static List<NpcInstance> getNpcs() {
		return _npcs;
	}

	public static void addOlympiadNpc(NpcInstance npc) {
		_npcs.add(npc);
	}

	public static void changeNobleName(int objId, String newName) {
		StatsSet noble = _nobles.get(objId);
		if (noble == null)
			return;
		noble.set(CHAR_NAME, newName);
		OlympiadDatabase.saveNobleData(objId);
	}

	public static String getNobleName(int objId) {
		StatsSet noble = _nobles.get(objId);
		if (noble == null)
			return null;
		return noble.getString(CHAR_NAME);
	}

	public static void manualSetNoblePoints(int objId, int points) {
		StatsSet noble = _nobles.get(objId);
		if (noble == null)
			return;
		noble.set(POINTS, points);
		OlympiadDatabase.saveNobleData(objId);
	}

	public static synchronized boolean isNoble(int objId) {
		return _nobles.get(objId) != null;
	}

	public static synchronized void addNoble(Player noble) {
		if (!_nobles.containsKey(noble.getObjectId())) {
			int classId = noble.getActiveClass().getFirstClassId();
			if (classId < 88) // Đ•Ń�Đ»Đ¸ ŃŤŃ‚Đľ Đ˝Đµ 3-ŃŹ ĐżŃ€ĐľŃ„Đ°, Ń‚Đľ Đ¸Ń�ĐżŃ€Đ°Đ˛Đ»ŃŹĐµĐĽ Ń�Đľ 2-Đą
								// Đ˝Đ° 3-ŃŽ.
				for (ClassId id : ClassId.VALUES)
					if (id.level() == 3 && id.getParent(0).getId() == classId) {
						classId = id.getId();
						break;
					}

			StatsSet statDat = new StatsSet();
			statDat.set(CHAR_NAME, noble.getName());
			statDat.set(GAME_TEAM2_COUNT, 0);
			statDat.set(GAME_TEAM4_COUNT, 0);
			statDat.set(GAME_TEAM6_COUNT, 0);

			_nobles.put(noble.getObjectId(), statDat);
			OlympiadDatabase.saveNobleData();
		}
	}

	public static synchronized void removeNoble(Player noble) {
		_nobles.remove(noble.getObjectId());
		OlympiadDatabase.saveNobleData();
	}
}
// EOF C:\l2sq\Pac
// Project\Java\l2ft\gameserver\model\entity\olympiad\Olympiad.java
