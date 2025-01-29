package l2ft.gameserver.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import l2ft.commons.dbutils.DbUtils;
import l2ft.gameserver.database.DatabaseFactory;
import l2ft.gameserver.model.ManageBbsTargets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Persists "ignored NPC IDs" in a table, e.g. bbs_targets.
 * Similar to CommunityPathsDAO, but simpler.
 */
public final class CommunityTargetsDAO {
    private static final Logger _log = LoggerFactory.getLogger(CommunityTargetsDAO.class);
    private static final CommunityTargetsDAO _instance = new CommunityTargetsDAO();

    // Example schema for bbs_targets:
    // CREATE TABLE IF NOT EXISTS `bbs_targets` (
    // `charId` INT NOT NULL,
    // `npcIds` VARCHAR(2048) NOT NULL, -- comma-separated
    // PRIMARY KEY (`charId`)
    // );
    private static final String SELECT_ALL = "SELECT charId,npcIds FROM bbs_targets";
    private static final String INSERT_OR_UPDATE = "REPLACE INTO bbs_targets (charId, npcIds) VALUES (?, ?)";
    private static final String DELETE = "DELETE FROM bbs_targets WHERE charId=?";

    public static CommunityTargetsDAO getInstance() {
        return _instance;
    }

    /**
     * Load all records from DB into ManageBbsTargets in-memory map.
     */
    public void selectAll() {
        Connection con = null;
        PreparedStatement stmt = null;
        ResultSet rset = null;
        try {
            con = DatabaseFactory.getInstance().getConnection();
            stmt = con.prepareStatement(SELECT_ALL);
            rset = stmt.executeQuery();
            while (rset.next()) {
                int charId = rset.getInt("charId");
                String npcIdsStr = rset.getString("npcIds");
                List<Integer> list = parseNpcIds(npcIdsStr);

                // store in ManageBbsTargets
                java.util.Set<Integer> set = new java.util.HashSet<Integer>(list);
                ManageBbsTargets.getInstance().setIgnored(charId, set);
            }
        } catch (Exception e) {
            _log.error("CommunityTargetsDAO: selectAll() failed", e);
        } finally {
            DbUtils.closeQuietly(con, stmt, rset);
        }
    }

    /**
     * Saves the entire ignore-set for a single charId to DB.
     * If set is empty, we can either store empty string or call delete.
     * We'll store an empty string, so the row is still present.
     */
    public void savePlayerIgnoreSet(int charId, List<Integer> ignoredList) {
        String npcIdsStr = buildCommaString(ignoredList);
        Connection con = null;
        PreparedStatement stmt = null;
        try {
            con = DatabaseFactory.getInstance().getConnection();
            stmt = con.prepareStatement(INSERT_OR_UPDATE);
            stmt.setInt(1, charId);
            stmt.setString(2, npcIdsStr);
            stmt.executeUpdate();
        } catch (Exception e) {
            _log.error("CommunityTargetsDAO: savePlayerIgnoreSet() failed charId=" + charId, e);
        } finally {
            DbUtils.closeQuietly(con, stmt);
        }
    }

    /**
     * Helper to build "1,2,3" from a list of ints.
     */
    private String buildCommaString(List<Integer> vals) {
        if (vals == null || vals.isEmpty())
            return "";
        StringBuilder sb = new StringBuilder();
        for (Integer v : vals) {
            sb.append(v).append(",");
        }
        // remove trailing comma
        if (sb.length() > 0)
            sb.setLength(sb.length() - 1);
        return sb.toString();
    }

    /**
     * Helper to parse "1,2,3" => list of integers.
     */
    private List<Integer> parseNpcIds(String str) {
        List<Integer> list = new ArrayList<Integer>();
        if (str == null || str.isEmpty())
            return list;
        String[] tokens = str.split(",");
        for (String t : tokens) {
            try {
                int val = Integer.parseInt(t.trim());
                list.add(val);
            } catch (NumberFormatException e) {
                // ignore
            }
        }
        return list;
    }
}
