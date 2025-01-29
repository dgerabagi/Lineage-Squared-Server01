//
// File: C:\l2sq\Pac Project\Java\l2ft\gameserver\dao\CharacterDAO.java
//
package l2ft.gameserver.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import org.apache.commons.lang3.StringUtils;
import l2ft.commons.dbutils.DbUtils;
import l2ft.gameserver.database.DatabaseFactory;
import l2ft.gameserver.model.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CharacterDAO {
	private static final Logger _log = LoggerFactory.getLogger(CharacterDAO.class);

	private static CharacterDAO _instance = new CharacterDAO();

	public static CharacterDAO getInstance() {
		return _instance;
	}

	public void deleteCharByObjId(int objid) {
		if (objid < 0) {
			return;
		}
		Connection con = null;
		PreparedStatement statement = null;
		try {
			con = DatabaseFactory.getInstance().getConnection();
			statement = con.prepareStatement("DELETE FROM characters WHERE obj_Id=?");
			statement.setInt(1, objid);
			statement.execute();
		} catch (Exception e) {
			_log.error("deleteCharByObjId: ", e);
		} finally {
			DbUtils.closeQuietly(con, statement);
		}
	}

	/**
	 * Insert the newly created Player into `characters` and `character_subclasses`.
	 */
	public boolean insert(Player player) {
		Connection con = null;
		PreparedStatement statement = null;
		try {
			con = DatabaseFactory.getInstance().getConnection();

			// -----------------------------------------------------------
			// 1) Insert row into `characters`, including primary_class_template
			// -----------------------------------------------------------
			statement = con.prepareStatement(
					"INSERT INTO `characters` ("
							+ "account_name, obj_Id, char_name, face, hairStyle, hairColor, sex,"
							+ "karma, pvpkills, pkkills, clanid, createtime, deletetime, title,"
							+ "accesslevel, online, leaveclan, deleteclan, nochannel, pledge_type,"
							+ "pledge_rank, lvl_joined_academy, apprentice,"
							+ "primary_class_template" // <--- Added this column
							+ ") VALUES ("
							+ "?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?" // <--- +1 placeholder
							+ ")");
			statement.setString(1, player.getAccountName());
			statement.setInt(2, player.getObjectId());
			statement.setString(3, player.getName());
			statement.setInt(4, player.getFace());
			statement.setInt(5, player.getHairStyle());
			statement.setInt(6, player.getHairColor());
			statement.setInt(7, player.getSex());
			statement.setInt(8, player.getKarma());
			statement.setInt(9, player.getPvpKills());
			statement.setInt(10, player.getPkKills());
			statement.setInt(11, player.getClanId());
			statement.setLong(12, player.getCreateTime() / 1000);
			statement.setInt(13, player.getDeleteTimer());
			statement.setString(14, player.getTitle());
			statement.setInt(15, player.getAccessLevel());
			statement.setInt(16, player.isOnline() ? 1 : 0);
			statement.setLong(17, player.getLeaveClanTime() / 1000);
			statement.setLong(18, player.getDeleteClanTime() / 1000);
			statement.setLong(19, 0); // "nochannel"?
			statement.setInt(20, player.getPledgeType());
			statement.setInt(21, player.getPowerGrade());
			statement.setInt(22, player.getLvlJoinedAcademy());
			statement.setInt(23, player.getApprentice());

			// The newly added column for the "primary_class_template"
			statement.setInt(24, player.getPrimaryClass()); // e.g. the classId from setPrimaryClass

			statement.executeUpdate();
			DbUtils.close(statement);

			// -----------------------------------------------------------
			// 2) Insert row into `character_subclasses` (base sub)
			// -----------------------------------------------------------
			statement = con.prepareStatement(
					"INSERT INTO character_subclasses "
							+ "(char_obj_id, class_id, exp, sp, curHp, curMp, curCp, maxHp, maxMp, maxCp, "
							+ " level, active, isBase, death_penalty, certification) "
							+ "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)");
			statement.setInt(1, player.getObjectId());
			statement.setInt(2, player.getTemplate().classId.getId());
			statement.setInt(3, 0);
			statement.setInt(4, 0);
			statement.setDouble(5,
					player.getTemplate().baseHpMax + player.getTemplate().lvlHpAdd + player.getTemplate().lvlHpMod);
			statement.setDouble(6,
					player.getTemplate().baseMpMax + player.getTemplate().lvlMpAdd + player.getTemplate().lvlMpMod);
			statement.setDouble(7,
					player.getTemplate().baseCpMax + player.getTemplate().lvlCpAdd + player.getTemplate().lvlCpMod);
			statement.setDouble(8,
					player.getTemplate().baseHpMax + player.getTemplate().lvlHpAdd + player.getTemplate().lvlHpMod);
			statement.setDouble(9,
					player.getTemplate().baseMpMax + player.getTemplate().lvlMpAdd + player.getTemplate().lvlMpMod);
			statement.setDouble(10,
					player.getTemplate().baseCpMax + player.getTemplate().lvlCpAdd + player.getTemplate().lvlCpMod);
			statement.setInt(11, 1); // level
			statement.setInt(12, 1); // active
			statement.setInt(13, 1); // isBase
			statement.setInt(14, 0);
			statement.setInt(15, 0);

			statement.executeUpdate();
		} catch (final Exception e) {
			_log.error("insert: ", e);
			return false;
		} finally {
			DbUtils.closeQuietly(con, statement);
		}
		return true;
	}

	/**
	 * Example method (unchanged from snippet) for retrieving recHave, etc...
	 */
	public int getDesiredRecHave(Player player) {
		int desiredRecHave = -1;
		String accountName = player.getAccountName();
		Connection con = null;
		PreparedStatement ps = null;
		ResultSet rs = null;

		try {
			con = DatabaseFactory.getInstance().getConnection();
			ps = con.prepareStatement("SELECT desired_rec_have FROM l2sq.acp_players WHERE account = ?");
			ps.setString(1, accountName);
			rs = ps.executeQuery();
			if (rs.next()) {
				desiredRecHave = rs.getInt("desired_rec_have");
			}
		} catch (Exception e) {
			_log.warn("getDesiredRecHave() failed for account {}: {}", accountName, e.getMessage());
		} finally {
			DbUtils.closeQuietly(con, ps, rs);
		}
		return desiredRecHave;
	}

	public void updateRecHaveInCharacters(Player player, int newRecHave) {
		Connection con = null;
		PreparedStatement ps = null;
		try {
			con = DatabaseFactory.getInstance().getConnection();
			ps = con.prepareStatement("UPDATE characters SET rec_have=? WHERE obj_Id=?");
			ps.setInt(1, newRecHave);
			ps.setInt(2, player.getObjectId());
			ps.executeUpdate();
		} catch (Exception e) {
			_log.warn("updateRecHaveInCharacters() failed for player "
					+ player.getName()
					+ ", recHave=" + newRecHave, e);
		} finally {
			DbUtils.closeQuietly(con, ps);
		}
	}

	public int getObjectIdByName(String name) {
		int result = 0;
		Connection con = null;
		PreparedStatement statement = null;
		ResultSet rset = null;
		try {
			con = DatabaseFactory.getInstance().getConnection();
			statement = con.prepareStatement("SELECT obj_Id FROM characters WHERE char_name=?");
			statement.setString(1, name);
			rset = statement.executeQuery();
			if (rset.next()) {
				result = rset.getInt(1);
			}
		} catch (Exception e) {
			_log.error("getObjectIdByName(String): ", e);
		} finally {
			DbUtils.closeQuietly(con, statement, rset);
		}
		return result;
	}

	public String getNameByObjectId(int objectId) {
		String result = StringUtils.EMPTY;
		Connection con = null;
		PreparedStatement statement = null;
		ResultSet rset = null;
		try {
			con = DatabaseFactory.getInstance().getConnection();
			statement = con.prepareStatement("SELECT char_name FROM characters WHERE obj_Id=?");
			statement.setInt(1, objectId);
			rset = statement.executeQuery();
			if (rset.next()) {
				result = rset.getString(1);
			}
		} catch (Exception e) {
			_log.error("getNameByObjectId(int): ", e);
		} finally {
			DbUtils.closeQuietly(con, statement, rset);
		}
		return result;
	}

	public int accountCharNumber(String account) {
		int number = 0;
		Connection con = null;
		PreparedStatement statement = null;
		ResultSet rset = null;
		try {
			con = DatabaseFactory.getInstance().getConnection();
			statement = con.prepareStatement("SELECT COUNT(char_name) FROM characters WHERE account_name=?");
			statement.setString(1, account);
			rset = statement.executeQuery();
			if (rset.next()) {
				number = rset.getInt(1);
			}
		} catch (Exception e) {
			_log.error("accountCharNumber: ", e);
		} finally {
			DbUtils.closeQuietly(con, statement, rset);
		}
		return number;
	}
}
