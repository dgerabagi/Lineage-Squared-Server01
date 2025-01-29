// C:\l2sq\Pac Project\Java\l2ft\gameserver\model\entity\olympiad\OlympiadDatabase.java
package l2ft.gameserver.model.entity.olympiad;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import l2ft.commons.dbutils.DbUtils;
import l2ft.gameserver.Announcements;
import l2ft.gameserver.Config;
import l2ft.gameserver.dao.OlympiadNobleDAO;
import l2ft.gameserver.database.DatabaseFactory;
import l2ft.gameserver.instancemanager.ServerVariables;
import l2ft.gameserver.model.entity.Hero;
import l2ft.gameserver.network.l2.components.SystemMsg;
import l2ft.gameserver.network.l2.s2c.SystemMessage2;
import l2ft.gameserver.templates.StatsSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OlympiadDatabase {
	private static final Logger _log = LoggerFactory.getLogger(OlympiadDatabase.class);

	public static synchronized void loadNoblesRank() {
		Olympiad._noblesRank = new ConcurrentHashMap<Integer, Integer>();
		Map<Integer, Integer> tmpPlace = new HashMap<Integer, Integer>();

		Connection con = null;
		PreparedStatement statement = null;
		ResultSet rset = null;
		try {
			con = DatabaseFactory.getInstance().getConnection();
			statement = con.prepareStatement(OlympiadNobleDAO.GET_ALL_CLASSIFIED_NOBLESS);
			rset = statement.executeQuery();
			int place = 1;
			while (rset.next())
				tmpPlace.put(rset.getInt(Olympiad.CHAR_ID), place++);

		} catch (Exception e) {
			_log.error("Olympiad System: Error!", e);
		} finally {
			DbUtils.closeQuietly(con, statement, rset);
		}

		int rank1 = (int) Math.round(tmpPlace.size() * 0.01);
		int rank2 = (int) Math.round(tmpPlace.size() * 0.10);
		int rank3 = (int) Math.round(tmpPlace.size() * 0.25);
		int rank4 = (int) Math.round(tmpPlace.size() * 0.50);

		if (rank1 == 0) {
			rank1 = 1;
			rank2++;
			rank3++;
			rank4++;
		}

		for (int charId : tmpPlace.keySet())
			if (tmpPlace.get(charId) <= rank1)
				Olympiad._noblesRank.put(charId, 1);
			else if (tmpPlace.get(charId) <= rank2)
				Olympiad._noblesRank.put(charId, 2);
			else if (tmpPlace.get(charId) <= rank3)
				Olympiad._noblesRank.put(charId, 3);
			else if (tmpPlace.get(charId) <= rank4)
				Olympiad._noblesRank.put(charId, 4);
			else
				Olympiad._noblesRank.put(charId, 5);
	}

	/**
	 * Сбрасывает информацию о ноблесах, сохраняя очки за предыдущий период
	 */
	public static synchronized void cleanupNobles() {
		_log.info("Olympiad: Calculating last period...");
		Connection con = null;
		PreparedStatement statement = null;
		try {
			con = DatabaseFactory.getInstance().getConnection();
			statement = con.prepareStatement(OlympiadNobleDAO.OLYMPIAD_CALCULATE_LAST_PERIOD);
			statement.setInt(1, Config.OLYMPIAD_BATTLES_FOR_REWARD);
			statement.execute();
			DbUtils.close(statement);

			statement = con.prepareStatement(OlympiadNobleDAO.OLYMPIAD_CLEANUP_POINTS);
			statement.setInt(1, Config.OLYMPIAD_POINTS_DEFAULT);
			statement.execute();
			DbUtils.close(statement);

			statement = con.prepareStatement(OlympiadNobleDAO.OLYMPIAD_CLEANUP_NOBLES);
			statement.execute();
		} catch (Exception e) {
			_log.error("Olympiad System: Couldn't calculate last period!", e);
		} finally {
			DbUtils.closeQuietly(con, statement);
		}

		for (Integer nobleId : Olympiad._nobles.keySet()) {
			StatsSet nobleInfo = Olympiad._nobles.get(nobleId);
			for (Entry<String, Object> entry : nobleInfo.entrySet()) {
				String text = entry.getKey();
				if (text.startsWith(Olympiad.POINTS)
						&& !text.substring(Olympiad.POINTS.length(), Olympiad.POINTS.length() + 1).equals("_")) {
					int combination = Integer.parseInt(text.substring(Olympiad.POINTS.length()));
					int points = (int) entry.getValue();
					int compDone = nobleInfo.getInteger(Olympiad.COMP_DONE + combination);
					if (compDone >= Config.OLYMPIAD_BATTLES_FOR_REWARD) {
						nobleInfo.set(Olympiad.POINTS_PAST + combination, points);
						nobleInfo.set(Olympiad.POINTS_PAST_STATIC + combination, points);
					} else {
						nobleInfo.set(Olympiad.POINTS_PAST + combination, 0);
						nobleInfo.set(Olympiad.POINTS_PAST_STATIC + combination, 0);
					}
					nobleInfo.set(text, Config.OLYMPIAD_POINTS_DEFAULT);
					nobleInfo.set(Olympiad.COMP_DONE + combination, 0);
					nobleInfo.set(Olympiad.COMP_WIN + combination, 0);
					nobleInfo.set(Olympiad.COMP_LOOSE + combination, 0);
					nobleInfo.set(Olympiad.GAME_TEAM2_COUNT + combination, 0);
					nobleInfo.set(Olympiad.GAME_TEAM4_COUNT + combination, 0);
					nobleInfo.set(Olympiad.GAME_TEAM6_COUNT + combination, 0);
					break;
				}
			}
		}
	}

	/**
	 * public static List<String> getClassLeaderBoard(int classId)
	 * {
	 * List<String> names = new ArrayList<String>();
	 * 
	 * Connection con = null;
	 * PreparedStatement statement = null;
	 * ResultSet rset = null;
	 * 
	 * try
	 * {
	 * con = DatabaseFactory.getInstance().getConnection();
	 * statement = con.prepareStatement(classId == 132 ?
	 * OlympiadNobleDAO.GET_EACH_CLASS_LEADER_SOULHOUND :
	 * OlympiadNobleDAO.GET_EACH_CLASS_LEADER);
	 * statement.setInt(1, classId);
	 * rset = statement.executeQuery();
	 * while(rset.next())
	 * names.add(rset.getString(Olympiad.CHAR_NAME));
	 * }
	 * catch(Exception e)
	 * {
	 * _log.error("Olympiad System: Couldnt get heros from db!", e);
	 * }
	 * finally
	 * {
	 * DbUtils.closeQuietly(con, statement, rset);
	 * }
	 * 
	 * return names;
	 * }
	 */

	public static synchronized void sortHerosToBe() {
		if (Olympiad._period != 1)
			return;

		Olympiad._heroesToBe = new ArrayList<StatsSet>();

		Connection con = null;
		PreparedStatement statement = null;
		ResultSet rset = null;
		try {
			con = DatabaseFactory.getInstance().getConnection();
			StatsSet hero;

			statement = con.prepareStatement(OlympiadNobleDAO.OLYMPIAD_GET_HEROS);
			statement.setInt(1, Config.OLYMPIAD_BATTLES_FOR_REWARD);
			rset = statement.executeQuery();
			int heroesToChoose = Config.OLYMPIAD_HEROES;
			int index = 0;
			while (rset.next() && index < heroesToChoose) {
				hero = new StatsSet();
				hero.set(Olympiad.CHAR_ID, rset.getInt(Olympiad.CHAR_ID));
				hero.set(Hero.COMBINATION, rset.getInt(Olympiad.CLASS_COMBINATION));
				hero.set(Olympiad.CHAR_NAME, rset.getString(Olympiad.CHAR_NAME));
				Olympiad._heroesToBe.add(hero);
				index++;
			}
		} catch (Exception e) {
			_log.error("Olympiad System: Couldnt heros from db!", e);
		} finally {
			DbUtils.closeQuietly(con, statement, rset);
		}
	}

	public static synchronized void saveNobleData(int nobleId) {
		OlympiadNobleDAO.getInstance().replacePlayer(nobleId);
	}

	public static synchronized void saveNobleData() {
		if (Olympiad._nobles == null)
			return;
		for (Integer nobleId : Olympiad._nobles.keySet())
			saveNobleData(nobleId);
	}

	public static synchronized void setNewOlympiadEnd() {
		Announcements.getInstance().announceToAll(
				new SystemMessage2(SystemMsg.OLYMPIAD_PERIOD_S1_HAS_STARTED).addInteger(Olympiad._currentCycle));

		Calendar currentTime = Calendar.getInstance();
		currentTime.set(Calendar.DAY_OF_WEEK, 1);
		currentTime.set(Calendar.HOUR_OF_DAY, 23);
		currentTime.set(Calendar.MINUTE, 59);
		if (currentTime.getTimeInMillis() < System.currentTimeMillis())
			currentTime.set(Calendar.WEEK_OF_YEAR, currentTime.get(Calendar.WEEK_OF_YEAR) + 1);

		Olympiad._olympiadEnd = currentTime.getTimeInMillis();

		Calendar nextChange = Calendar.getInstance();
		Olympiad._nextWeeklyChange = nextChange.getTimeInMillis() + Config.ALT_OLY_WPERIOD;

		Olympiad._isOlympiadEnd = false;
	}

	public static void save() {
		saveNobleData();
		ServerVariables.set("Olympiad_CurrentCycle", Olympiad._currentCycle);
		ServerVariables.set("Olympiad_Period", Olympiad._period);
		ServerVariables.set("Olympiad_End", Olympiad._olympiadEnd);
		ServerVariables.set("Olympiad_ValdationEnd", Olympiad._validationEnd);
		ServerVariables.set("Olympiad_NextWeeklyChange", Olympiad._nextWeeklyChange);
	}
}
// EOF C:\l2sq\Pac
// Project\Java\l2ft\gameserver\model\entity\olympiad\OlympiadDatabase.java
