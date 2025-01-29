// C:\l2sq\Pac Project\Java\l2ft\gameserver\dao\OlympiadNobleDAO.java
package l2ft.gameserver.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import l2ft.commons.dbutils.DbUtils;
import l2ft.gameserver.database.DatabaseFactory;
import l2ft.gameserver.model.entity.olympiad.Olympiad;
import l2ft.gameserver.templates.StatsSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author VISTALL
 * @date 20:00/02.05.2011
 */
public class OlympiadNobleDAO {
	private static final Logger _log = LoggerFactory.getLogger(OlympiadNobleDAO.class);
	private static final OlympiadNobleDAO _instance = new OlympiadNobleDAO();

	public static final String SELECT_PLAYER = "SELECT char_id, characters.char_name as char_name, game_team2_count, game_team4_count, game_team6_count FROM olympiad_nobles LEFT JOIN characters ON characters.obj_Id = olympiad_nobles.char_id";
	public static final String SELECT_POINTS = "SELECT char_id, characters.char_name as char_name, class_combination, olympiad_points, olympiad_points_past, olympiad_points_past_static, competitions_done, competitions_loose, competitions_win FROM olympiad_points LEFT JOIN characters ON characters.obj_Id = olympiad_points.char_id";
	public static final String REPLACE_PLAYER = "REPLACE INTO `olympiad_nobles` (`char_id`, game_team2_count, game_team4_count, game_team6_count) VALUES (?,?,?,?)";
	public static final String REPLACE_POINTS = "REPLACE INTO `olympiad_points` (`char_id`, class_combination, olympiad_points, olympiad_points_past, olympiad_points_past_static, competitions_done, competitions_win, competitions_loose) VALUES (?,?,?,?,?,?,?,?)";
	public static final String OLYMPIAD_GET_HEROS = "SELECT `char_id`, characters.char_name AS char_name, class_combination FROM `olympiad_points` LEFT JOIN characters ON char_id=characters.obj_Id WHERE `competitions_done` >= ? AND `competitions_win` > 0 GROUP BY char_id ORDER BY `olympiad_points` DESC, `competitions_win` DESC, `competitions_done` DESC";
	public static final String GET_ALL_CLASSIFIED_NOBLESS = "SELECT `char_id` FROM `olympiad_points` GROUP BY char_id ORDER BY olympiad_points_past_static DESC";
	public static final String OLYMPIAD_CALCULATE_LAST_PERIOD = "UPDATE `olympiad_points` SET `olympiad_points_past` = `olympiad_points`, `olympiad_points_past_static` = `olympiad_points` WHERE `competitions_done` >= ?";
	public static final String OLYMPIAD_CLEANUP_POINTS = "UPDATE `olympiad_points` SET `olympiad_points` = ?, `competitions_done` = 0, `competitions_win` = 0, `competitions_loose` = 0";
	public static final String OLYMPIAD_CLEANUP_NOBLES = "UPDATE `olympiad_nobles` SET game_team2_count=0, game_team4_count=0, game_team6_count=0";

	public static OlympiadNobleDAO getInstance() {
		return _instance;
	}

	public void select() {
		Connection con = null;
		PreparedStatement statement = null;
		ResultSet rset = null;
		try {
			con = DatabaseFactory.getInstance().getConnection();
			statement = con.prepareStatement(SELECT_PLAYER);
			rset = statement.executeQuery();
			while (rset.next()) {
				StatsSet statDat = new StatsSet();
				int charId = rset.getInt(Olympiad.CHAR_ID);
				statDat.set(Olympiad.CHAR_NAME, rset.getString(Olympiad.CHAR_NAME));
				statDat.set(Olympiad.GAME_TEAM2_COUNT, rset.getInt(Olympiad.GAME_TEAM2_COUNT));
				statDat.set(Olympiad.GAME_TEAM4_COUNT, rset.getInt(Olympiad.GAME_TEAM4_COUNT));
				statDat.set(Olympiad.GAME_TEAM6_COUNT, rset.getInt(Olympiad.GAME_TEAM6_COUNT));

				Olympiad._nobles.put(charId, statDat);
			}
			DbUtils.close(statement, rset);

			statement = con.prepareStatement(SELECT_POINTS);
			rset = statement.executeQuery();
			while (rset.next()) {
				int charId = rset.getInt(Olympiad.CHAR_ID);
				StatsSet statDat = Olympiad._nobles.get(charId);
				int combination = rset.getInt(Olympiad.CLASS_COMBINATION);
				if (statDat.get(Olympiad.CLASS_COMBINATION) != null)
					statDat.set(Olympiad.CLASS_COMBINATION,
							statDat.getString(Olympiad.CLASS_COMBINATION) + "," + combination);
				else
					statDat.set(Olympiad.CLASS_COMBINATION, combination);
				statDat.set(Olympiad.POINTS + combination, rset.getInt(Olympiad.POINTS));
				statDat.set(Olympiad.POINTS_PAST + combination, rset.getInt(Olympiad.POINTS_PAST));
				statDat.set(Olympiad.POINTS_PAST_STATIC + combination, rset.getInt(Olympiad.POINTS_PAST_STATIC));
				statDat.set(Olympiad.COMP_DONE + combination, rset.getInt(Olympiad.COMP_DONE));
				statDat.set(Olympiad.COMP_WIN + combination, rset.getInt(Olympiad.COMP_WIN));
				statDat.set(Olympiad.COMP_LOOSE + combination, rset.getInt(Olympiad.COMP_LOOSE));

				Olympiad._nobles.put(charId, statDat);
			}
		} catch (Exception e) {
			_log.error("OlympiadNobleDAO: select():", e);
		} finally {
			DbUtils.closeQuietly(con, statement, rset);
		}
	}

	public void replacePoints(int nobleId, int combination) {
		Connection con = null;
		PreparedStatement statement = null;
		try {
			con = DatabaseFactory.getInstance().getConnection();
			StatsSet nobleInfo = Olympiad._nobles.get(nobleId);

			statement = con.prepareStatement(REPLACE_POINTS);
			statement.setInt(1, nobleId);
			statement.setInt(2, combination);
			statement.setInt(3, nobleInfo.getInteger(Olympiad.POINTS + combination, 0));
			statement.setInt(4, nobleInfo.getInteger(Olympiad.POINTS_PAST + combination, 0));
			statement.setInt(5, nobleInfo.getInteger(Olympiad.POINTS_PAST_STATIC + combination, 0));
			statement.setInt(6, nobleInfo.getInteger(Olympiad.COMP_DONE + combination, 0));
			statement.setInt(7, nobleInfo.getInteger(Olympiad.COMP_WIN + combination, 0));
			statement.setInt(8, nobleInfo.getInteger(Olympiad.COMP_LOOSE + combination, 0));
			statement.execute();
		} catch (Exception e) {
			_log.error("OlympiadNobleDAO: replace(int): " + nobleId, e);
		} finally {
			DbUtils.closeQuietly(con, statement);
		}
	}

	public void replacePlayer(int nobleId) {
		Connection con = null;
		PreparedStatement statement = null;
		try {
			con = DatabaseFactory.getInstance().getConnection();
			StatsSet nobleInfo = Olympiad._nobles.get(nobleId);

			statement = con.prepareStatement(REPLACE_PLAYER);
			statement.setInt(1, nobleId);
			statement.setInt(2, nobleInfo.getInteger(Olympiad.GAME_TEAM2_COUNT));
			statement.setInt(3, nobleInfo.getInteger(Olympiad.GAME_TEAM4_COUNT));
			statement.setInt(4, nobleInfo.getInteger(Olympiad.GAME_TEAM6_COUNT));
			statement.execute();
		} catch (Exception e) {
			_log.error("OlympiadNobleDAO: replace(int): " + nobleId, e);
		} finally {
			DbUtils.closeQuietly(con, statement);
		}
	}
}
// EOF C:\l2sq\Pac Project\Java\l2ft\gameserver\dao\OlympiadNobleDAO.java
