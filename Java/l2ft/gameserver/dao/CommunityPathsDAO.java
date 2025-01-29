package l2ft.gameserver.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import l2ft.gameserver.model.ManageBbsPaths;
import l2ft.commons.dbutils.DbUtils;
import l2ft.gameserver.database.DatabaseFactory;
import l2ft.gameserver.model.Player;
import l2ft.gameserver.utils.Location;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CommunityPathsDAO {
    private static final Logger _log = LoggerFactory.getLogger(CommunityPathsDAO.class);
    private static final CommunityPathsDAO _instance = new CommunityPathsDAO();

    // Table is bbs_paths
    // columns: charId, pathName, points, designMode
    private static final String SELECT_SQL = "SELECT * FROM bbs_paths";
    private static final String INSERT_SQL = "INSERT INTO bbs_paths (charId, pathName, points, designMode) VALUES (?, ?, ?, ?)";
    private static final String UPDATE_SQL = "UPDATE bbs_paths SET points=?, designMode=? WHERE charId=? AND pathName=?";
    private static final String DELETE_SQL = "DELETE FROM bbs_paths WHERE charId=? AND pathName=?";

    public static CommunityPathsDAO getInstance() {
        return _instance;
    }

    /**
     * Loads all path entries from DB into the static path container
     * (similar to how buff schemas are loaded).
     */
    public void selectAll() {
        Connection con = null;
        PreparedStatement stmt = null;
        ResultSet rset = null;
        try {
            con = DatabaseFactory.getInstance().getConnection();
            stmt = con.prepareStatement(SELECT_SQL);
            rset = stmt.executeQuery();
            while (rset.next()) {
                int charId = rset.getInt("charId");
                String pName = rset.getString("pathName");
                String ptsString = rset.getString("points");
                int dmInt = rset.getInt("designMode");
                boolean dMode = (dmInt != 0);

                // parse the points
                List<Location> pointList = parsePoints(ptsString);

                // store it in ManageBbsPaths or similar
                // We'll do: ManageBbsPaths.getInstance().addPath(charId, pName, pointList,
                // dMode);
                ManageBbsPaths.getInstance().addPath(charId, pName, pointList, dMode);
            }
        } catch (Exception e) {
            _log.error("CommunityPathsDAO.selectAll(): " + e, e);
        } finally {
            DbUtils.closeQuietly(con, stmt, rset);
        }
    }

    public void insert(int charId, String pathName, List<Location> points, boolean designMode) {
        Connection con = null;
        PreparedStatement stmt = null;
        try {
            con = DatabaseFactory.getInstance().getConnection();
            stmt = con.prepareStatement(INSERT_SQL);
            stmt.setInt(1, charId);
            stmt.setString(2, pathName);
            stmt.setString(3, buildPointsString(points));
            stmt.setInt(4, designMode ? 1 : 0);
            stmt.executeUpdate();
        } catch (Exception e) {
            _log.error("CommunityPathsDAO.insert() failed: charId=" + charId + ", pathName=" + pathName, e);
        } finally {
            DbUtils.closeQuietly(con, stmt);
        }
    }

    public void update(int charId, String pathName, List<Location> points, boolean designMode) {
        Connection con = null;
        PreparedStatement stmt = null;
        try {
            con = DatabaseFactory.getInstance().getConnection();
            stmt = con.prepareStatement(UPDATE_SQL);
            stmt.setString(1, buildPointsString(points));
            stmt.setInt(2, designMode ? 1 : 0);
            stmt.setInt(3, charId);
            stmt.setString(4, pathName);
            stmt.executeUpdate();
        } catch (Exception e) {
            _log.error("CommunityPathsDAO.update() failed: charId=" + charId + ", pathName=" + pathName, e);
        } finally {
            DbUtils.closeQuietly(con, stmt);
        }
    }

    public void delete(int charId, String pathName) {
        Connection con = null;
        PreparedStatement stmt = null;
        try {
            con = DatabaseFactory.getInstance().getConnection();
            stmt = con.prepareStatement(DELETE_SQL);
            stmt.setInt(1, charId);
            stmt.setString(2, pathName);
            stmt.executeUpdate();
        } catch (Exception e) {
            _log.error("CommunityPathsDAO.delete() failed: charId=" + charId + ", pathName=" + pathName, e);
        } finally {
            DbUtils.closeQuietly(con, stmt);
        }
    }

    // Helpers
    private String buildPointsString(List<Location> points) {
        if (points == null || points.isEmpty())
            return "";
        StringBuilder sb = new StringBuilder();
        for (Location loc : points) {
            sb.append(loc.x).append(",").append(loc.y).append(",").append(loc.z).append(";");
        }
        return sb.toString();
    }

    private List<Location> parsePoints(String input) {
        List<Location> list = new ArrayList<Location>();
        if (input == null || input.isEmpty())
            return list;
        String[] tokens = input.split(";");
        for (String token : tokens) {
            token = token.trim();
            if (token.isEmpty())
                continue;
            String[] coords = token.split(",");
            if (coords.length < 3)
                continue;
            try {
                int x = Integer.parseInt(coords[0]);
                int y = Integer.parseInt(coords[1]);
                int z = Integer.parseInt(coords[2]);
                list.add(new Location(x, y, z));
            } catch (NumberFormatException e) {
                // skip
            }
        }
        return list;
    }
}
