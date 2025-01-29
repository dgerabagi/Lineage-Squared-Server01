package l2ft.gameserver.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.Map;

import l2ft.commons.dbutils.DbUtils;
import l2ft.gameserver.database.DatabaseFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import l2ft.gameserver.dao.ForgottenBattlegroundsDAO.PlayerStats;
import l2ft.gameserver.model.Player;

public class ForgottenBattlegroundsDAO {

    private static final Logger _log = LoggerFactory.getLogger(ForgottenBattlegroundsDAO.class);

    private static final ForgottenBattlegroundsDAO _instance = new ForgottenBattlegroundsDAO();

    public static ForgottenBattlegroundsDAO getInstance() {
        return _instance;
    }

    private static final String SELECT_PLAYER_STATS = "SELECT charId, totalKills, totalDeaths, totalWins, totalLosses, flagsDestroyed, currentELO, highestELO, totalMatches, lastActive, leaves FROM fbg_player_stats WHERE charId=?";
    private static final String INSERT_PLAYER_STATS = "INSERT INTO fbg_player_stats (charId, currentELO, highestELO, lastActive) VALUES (?,2000,2000,?)";
    private static final String UPDATE_PLAYER_STATS = "UPDATE fbg_player_stats SET totalKills=?, totalDeaths=?, totalWins=?, totalLosses=?, flagsDestroyed=?, currentELO=?, highestELO=?, totalMatches=?, lastActive=? WHERE charId=?";

    private static final String INSERT_MATCH = "INSERT INTO fbg_matches (startTime,winnerTeam,blueTeamSize,redTeamSize) VALUES(?,?,?,?)";
    private static final String GET_LAST_INSERT_ID = "SELECT LAST_INSERT_ID()";
    private static final String UPDATE_MATCH = "UPDATE fbg_matches SET endTime=?, winnerTeam=?, blueFlagsDestroyed=?, redFlagsDestroyed=? WHERE matchId=?";

    private static final String INSERT_MATCH_PARTICIPANT = "INSERT INTO fbg_match_participants (matchId,charId,team,kills,deaths,oldELO,primaryClassId,secondaryClassId) VALUES(?,?,?,?,?,?,?,?)";
    private static final String UPDATE_MATCH_PARTICIPANT = "UPDATE fbg_match_participants SET kills=?, deaths=?, newELO=? WHERE matchId=? AND charId=?";
    private static final String UPDATE_MATCH_PARTICIPANT_LEFT_EARLY = "UPDATE fbg_match_participants SET leftEarly=1 WHERE matchId=? AND charId=?";

    private static final String INSERT_ELO_HISTORY = "INSERT INTO fbg_elo_history (charId,timestamp,oldELO,newELO,matchId) VALUES(?,?,?,?,?)";

    private static final String UPDATE_LIVE_STATUS = "UPDATE fbg_live_status SET eventLive=?, currentMatchId=?, blueFlagsRemaining=?, redFlagsRemaining=?, blueFlagsDestroyed=?, redFlagsDestroyed=?, blueTeamSize=?, redTeamSize=?, blueTotalKills=?, redTotalKills=?, lastUpdate=? WHERE id=1";

    private static final String INSERT_KILL = "INSERT INTO fbg_kills (matchId, killerCharId, victimCharId, timestamp, eloDelta) VALUES (?,?,?,?,?)";
    private static final String CLEAR_LIVE_PLAYERS = "DELETE FROM fbg_live_players WHERE matchId=?";
    private static final String INSERT_LIVE_PLAYER = "INSERT INTO fbg_live_players (matchId,charId,team,currentELO,kills,deaths,primaryClassId,secondaryClassId,flagsDestroyed,lastUpdate) VALUES(?,?,?,?,?,?,?,?,?,?)";
    private static final String UPDATE_LIVE_PLAYER = "UPDATE fbg_live_players SET currentELO=?, kills=?, deaths=?, flagsDestroyed=?, lastUpdate=? WHERE matchId=? AND charId=?";
    private static final String INCREMENT_LEAVES = "UPDATE fbg_player_stats SET leaves=leaves+1 WHERE charId=?";
    private static final String INSERT_FLAG_CAPTURE = "INSERT INTO fbg_flag_captures (matchId,charId,timestamp,flagIndex) VALUES(?,?,?,?)";

    public void insertFlagCapture(int matchId, int charId, int flagIndex) {
        Connection con = null;
        PreparedStatement stmt = null;
        try {
            con = DatabaseFactory.getInstance().getConnection();
            stmt = con.prepareStatement(INSERT_FLAG_CAPTURE);
            Timestamp now = new Timestamp(System.currentTimeMillis());
            stmt.setInt(1, matchId);
            stmt.setInt(2, charId);
            stmt.setTimestamp(3, now);
            stmt.setInt(4, flagIndex);
            stmt.executeUpdate();
        } catch (Exception e) {
            _log.error("Error inserting flag capture", e);
        } finally {
            DbUtils.closeQuietly(con, stmt);
        }
    }

    public PlayerStats loadOrCreatePlayerStats(int charId) {
        PlayerStats ps = null;
        Connection con = null;
        PreparedStatement stmt = null;
        ResultSet rset = null;
        try {
            con = DatabaseFactory.getInstance().getConnection();
            stmt = con.prepareStatement(SELECT_PLAYER_STATS);
            stmt.setInt(1, charId);
            rset = stmt.executeQuery();
            if (rset.next()) {
                ps = new PlayerStats();
                ps.charId = rset.getInt("charId");
                ps.totalKills = rset.getInt("totalKills");
                ps.totalDeaths = rset.getInt("totalDeaths");
                ps.totalWins = rset.getInt("totalWins");
                ps.totalLosses = rset.getInt("totalLosses");
                ps.flagsDestroyed = rset.getInt("flagsDestroyed");
                ps.currentELO = rset.getInt("currentELO");
                ps.highestELO = rset.getInt("highestELO");
                ps.totalMatches = rset.getInt("totalMatches");
                ps.lastActive = rset.getTimestamp("lastActive");
            } else {
                DbUtils.closeQuietly(stmt, rset);
                stmt = con.prepareStatement(INSERT_PLAYER_STATS);
                Timestamp now = new Timestamp(System.currentTimeMillis());
                stmt.setInt(1, charId);
                stmt.setTimestamp(2, now);
                stmt.executeUpdate();
                DbUtils.closeQuietly(stmt);

                stmt = con.prepareStatement(SELECT_PLAYER_STATS);
                stmt.setInt(1, charId);
                rset = stmt.executeQuery();
                if (rset.next()) {
                    ps = new PlayerStats();
                    ps.charId = charId;
                    ps.totalKills = 0;
                    ps.totalDeaths = 0;
                    ps.totalWins = 0;
                    ps.totalLosses = 0;
                    ps.flagsDestroyed = 0;
                    ps.currentELO = 2000;
                    ps.highestELO = 2000;
                    ps.totalMatches = 0;
                    ps.lastActive = rset.getTimestamp("lastActive");
                }
            }
        } catch (Exception e) {
            _log.error("Error loading or creating player stats for charId=" + charId, e);
        } finally {
            DbUtils.closeQuietly(con, stmt, rset);
        }
        return ps;
    }

    public void updatePlayerStats(PlayerStats ps) {
        Connection con = null;
        PreparedStatement stmt = null;
        try {
            con = DatabaseFactory.getInstance().getConnection();
            stmt = con.prepareStatement(UPDATE_PLAYER_STATS);
            Timestamp now = new Timestamp(System.currentTimeMillis());
            stmt.setInt(1, ps.totalKills);
            stmt.setInt(2, ps.totalDeaths);
            stmt.setInt(3, ps.totalWins);
            stmt.setInt(4, ps.totalLosses);
            stmt.setInt(5, ps.flagsDestroyed);
            stmt.setInt(6, ps.currentELO);
            stmt.setInt(7, ps.highestELO);
            stmt.setInt(8, ps.totalMatches);
            stmt.setTimestamp(9, now);
            stmt.setInt(10, ps.charId);
            stmt.executeUpdate();
        } catch (Exception e) {
            _log.error("Error updating player stats for charId=" + ps.charId, e);
        } finally {
            DbUtils.closeQuietly(con, stmt);
        }
    }

    public int insertMatch(Timestamp startTime, String winnerTeam, int blueTeamSize, int redTeamSize) {
        Connection con = null;
        PreparedStatement stmt = null;
        ResultSet rset = null;
        int matchId = 0;
        try {
            con = DatabaseFactory.getInstance().getConnection();
            stmt = con.prepareStatement(INSERT_MATCH);
            stmt.setTimestamp(1, startTime);
            stmt.setString(2, winnerTeam);
            stmt.setInt(3, blueTeamSize);
            stmt.setInt(4, redTeamSize);
            stmt.executeUpdate();
            DbUtils.close(stmt);
            stmt = con.prepareStatement(GET_LAST_INSERT_ID);
            rset = stmt.executeQuery();
            if (rset.next()) {
                matchId = rset.getInt(1);
            }
        } catch (Exception e) {
            _log.error("Error inserting match", e);
        } finally {
            DbUtils.closeQuietly(con, stmt, rset);
        }
        return matchId;
    }

    public void updateMatch(int matchId, Timestamp endTime, String winnerTeam, int blueFlagsDestroyed,
            int redFlagsDestroyed) {
        Connection con = null;
        PreparedStatement stmt = null;
        try {
            con = DatabaseFactory.getInstance().getConnection();
            stmt = con.prepareStatement(UPDATE_MATCH);
            stmt.setTimestamp(1, endTime);
            stmt.setString(2, winnerTeam);
            stmt.setInt(3, blueFlagsDestroyed);
            stmt.setInt(4, redFlagsDestroyed);
            stmt.setInt(5, matchId);
            stmt.executeUpdate();
        } catch (Exception e) {
            _log.error("Error updating matchId=" + matchId, e);
        } finally {
            DbUtils.closeQuietly(con, stmt);
        }
    }

    public void insertMatchParticipant(int matchId, int charId, String team, int oldELO, int primaryClassId,
            int secondaryClassId) {
        Connection con = null;
        PreparedStatement stmt = null;
        try {
            con = DatabaseFactory.getInstance().getConnection();
            stmt = con.prepareStatement(INSERT_MATCH_PARTICIPANT);
            stmt.setInt(1, matchId);
            stmt.setInt(2, charId);
            stmt.setString(3, team);
            stmt.setInt(4, 0); // initial kills
            stmt.setInt(5, 0); // initial deaths
            stmt.setInt(6, oldELO);
            stmt.setInt(7, primaryClassId);
            stmt.setInt(8, secondaryClassId);
            stmt.executeUpdate();
        } catch (Exception e) {
            _log.error("Error inserting match participant charId=" + charId + " matchId=" + matchId, e);
        } finally {
            DbUtils.closeQuietly(con, stmt);
        }
    }

    public void updateMatchParticipant(int matchId, int charId, int kills, int deaths, int newELO) {
        Connection con = null;
        PreparedStatement stmt = null;
        try {
            con = DatabaseFactory.getInstance().getConnection();
            stmt = con.prepareStatement(UPDATE_MATCH_PARTICIPANT);
            stmt.setInt(1, kills);
            stmt.setInt(2, deaths);
            stmt.setInt(3, newELO);
            stmt.setInt(4, matchId);
            stmt.setInt(5, charId);
            stmt.executeUpdate();
        } catch (Exception e) {
            _log.error("Error updating match participant charId=" + charId + " matchId=" + matchId, e);
        } finally {
            DbUtils.closeQuietly(con, stmt);
        }
    }

    public void setParticipantLeftEarly(int matchId, int charId) {
        Connection con = null;
        PreparedStatement stmt = null;
        try {
            con = DatabaseFactory.getInstance().getConnection();
            stmt = con.prepareStatement(UPDATE_MATCH_PARTICIPANT_LEFT_EARLY);
            stmt.setInt(1, matchId);
            stmt.setInt(2, charId);
            stmt.executeUpdate();
        } catch (Exception e) {
            _log.error("Error setting leftEarly for matchId=" + matchId + " charId=" + charId, e);
        } finally {
            DbUtils.closeQuietly(con, stmt);
        }
    }

    public void insertEloHistory(int charId, int oldELO, int newELO, int matchId) {
        Connection con = null;
        PreparedStatement stmt = null;
        try {
            con = DatabaseFactory.getInstance().getConnection();
            stmt = con.prepareStatement(INSERT_ELO_HISTORY);
            Timestamp now = new Timestamp(System.currentTimeMillis());
            stmt.setInt(1, charId);
            stmt.setTimestamp(2, now);
            stmt.setInt(3, oldELO);
            stmt.setInt(4, newELO);
            stmt.setInt(5, matchId == 0 ? 0 : matchId);
            stmt.executeUpdate();
        } catch (Exception e) {
            _log.error("Error inserting ELO history charId=" + charId, e);
        } finally {
            DbUtils.closeQuietly(con, stmt);
        }
    }

    public void updateLiveStatus(boolean eventLive, int currentMatchId, int blueFlagsRemaining, int redFlagsRemaining,
            int blueFlagsDestroyed, int redFlagsDestroyed,
            int blueTeamSize, int redTeamSize,
            int blueTotalKills, int redTotalKills) {
        Connection con = null;
        PreparedStatement stmt = null;
        try {
            con = DatabaseFactory.getInstance().getConnection();
            stmt = con.prepareStatement(UPDATE_LIVE_STATUS);
            Timestamp now = new Timestamp(System.currentTimeMillis());
            stmt.setBoolean(1, eventLive);
            stmt.setInt(2, currentMatchId);
            stmt.setInt(3, blueFlagsRemaining);
            stmt.setInt(4, redFlagsRemaining);
            stmt.setInt(5, blueFlagsDestroyed);
            stmt.setInt(6, redFlagsDestroyed);
            stmt.setInt(7, blueTeamSize);
            stmt.setInt(8, redTeamSize);
            stmt.setInt(9, blueTotalKills);
            stmt.setInt(10, redTotalKills);
            stmt.setTimestamp(11, now);
            stmt.executeUpdate();
        } catch (Exception e) {
            _log.error("Error updating live status", e);
        } finally {
            DbUtils.closeQuietly(con, stmt);
        }
    }

    public void insertKill(int matchId, int killerCharId, int victimCharId, int eloDelta) {
        Connection con = null;
        PreparedStatement stmt = null;
        try {
            con = DatabaseFactory.getInstance().getConnection();
            stmt = con.prepareStatement(INSERT_KILL);
            Timestamp now = new Timestamp(System.currentTimeMillis());
            stmt.setInt(1, matchId);
            stmt.setInt(2, killerCharId);
            stmt.setInt(3, victimCharId);
            stmt.setTimestamp(4, now);
            stmt.setInt(5, eloDelta);
            stmt.executeUpdate();
        } catch (Exception e) {
            _log.error("Error inserting kill record", e);
        } finally {
            DbUtils.closeQuietly(con, stmt);
        }
    }

    public void refreshLivePlayers(int matchId,
            Player[] bluePlayers,
            Player[] redPlayers,
            Map<Integer, Integer> killsMap,
            Map<Integer, Integer> deathsMap,
            Map<Integer, Integer> flagsMap,
            Map<Integer, Integer> currentEloMap,
            Map<Integer, Integer> pClassMap,
            Map<Integer, Integer> sClassMap) {
        Connection con = null;
        PreparedStatement stmt = null;
        try {
            con = DatabaseFactory.getInstance().getConnection();
            // Clear existing
            stmt = con.prepareStatement(CLEAR_LIVE_PLAYERS);
            stmt.setInt(1, matchId);
            stmt.executeUpdate();
            DbUtils.closeQuietly(stmt);

            stmt = con.prepareStatement(INSERT_LIVE_PLAYER);
            Timestamp now = new Timestamp(System.currentTimeMillis());

            if (bluePlayers != null) {
                for (Player p : bluePlayers) {
                    if (p == null)
                        continue;
                    int cElo = currentEloMap.containsKey(p.getObjectId()) ? currentEloMap.get(p.getObjectId()) : 2000;
                    int k = killsMap.containsKey(p.getObjectId()) ? killsMap.get(p.getObjectId()) : 0;
                    int d = deathsMap.containsKey(p.getObjectId()) ? deathsMap.get(p.getObjectId()) : 0;
                    int f = flagsMap.containsKey(p.getObjectId()) ? flagsMap.get(p.getObjectId()) : 0;
                    int pc = pClassMap.containsKey(p.getObjectId()) ? pClassMap.get(p.getObjectId()) : 0;
                    int sc = sClassMap.containsKey(p.getObjectId()) ? sClassMap.get(p.getObjectId()) : 0;
                    stmt.setInt(1, matchId);
                    stmt.setInt(2, p.getObjectId());
                    stmt.setString(3, "BLUE");
                    stmt.setInt(4, cElo);
                    stmt.setInt(5, k);
                    stmt.setInt(6, d);
                    stmt.setInt(7, pc);
                    stmt.setInt(8, sc);
                    stmt.setInt(9, f);
                    stmt.setTimestamp(10, now);
                    stmt.addBatch();
                }
            }

            if (redPlayers != null) {
                for (Player p : redPlayers) {
                    if (p == null)
                        continue;
                    int cElo = currentEloMap.containsKey(p.getObjectId()) ? currentEloMap.get(p.getObjectId()) : 2000;
                    int k = killsMap.containsKey(p.getObjectId()) ? killsMap.get(p.getObjectId()) : 0;
                    int d = deathsMap.containsKey(p.getObjectId()) ? deathsMap.get(p.getObjectId()) : 0;
                    int f = flagsMap.containsKey(p.getObjectId()) ? flagsMap.get(p.getObjectId()) : 0;
                    int pc = pClassMap.containsKey(p.getObjectId()) ? pClassMap.get(p.getObjectId()) : 0;
                    int sc = sClassMap.containsKey(p.getObjectId()) ? sClassMap.get(p.getObjectId()) : 0;
                    stmt.setInt(1, matchId);
                    stmt.setInt(2, p.getObjectId());
                    stmt.setString(3, "RED");
                    stmt.setInt(4, cElo);
                    stmt.setInt(5, k);
                    stmt.setInt(6, d);
                    stmt.setInt(7, pc);
                    stmt.setInt(8, sc);
                    stmt.setInt(9, f);
                    stmt.setTimestamp(10, now);
                    stmt.addBatch();
                }
            }

            stmt.executeBatch();

        } catch (Exception e) {
            _log.error("Error refreshing live players", e);
        } finally {
            DbUtils.closeQuietly(con, stmt);
        }
    }

    public void updateLivePlayer(int matchId, int charId, int currentELO, int kills, int deaths, int flagsDestroyed) {
        Connection con = null;
        PreparedStatement stmt = null;
        try {
            con = DatabaseFactory.getInstance().getConnection();
            stmt = con.prepareStatement(UPDATE_LIVE_PLAYER);
            Timestamp now = new Timestamp(System.currentTimeMillis());
            stmt.setInt(1, currentELO);
            stmt.setInt(2, kills);
            stmt.setInt(3, deaths);
            stmt.setInt(4, flagsDestroyed);
            stmt.setTimestamp(5, now);
            stmt.setInt(6, matchId);
            stmt.setInt(7, charId);
            stmt.executeUpdate();
        } catch (Exception e) {
            _log.error("Error updating live player", e);
        } finally {
            DbUtils.closeQuietly(con, stmt);
        }
    }

    public void incrementLeaves(int charId) {
        Connection con = null;
        PreparedStatement stmt = null;
        try {
            con = DatabaseFactory.getInstance().getConnection();
            stmt = con.prepareStatement(INCREMENT_LEAVES);
            stmt.setInt(1, charId);
            stmt.executeUpdate();
        } catch (Exception e) {
            _log.error("Error incrementing leaves for charId=" + charId, e);
        } finally {
            DbUtils.closeQuietly(con, stmt);
        }
    }

    /**
     * Returns a multiplier for each class tier (F, D, C, B, A, S).
     * You can tweak the exact multipliers as needed.
     */
    public double getClassMultiplier(int classId) {
        if (classId == 88 || classId == 117) {
            // F-tier
            return 1.10;
        }
        if (classId == 96 || classId == 97 || classId == 98 || classId == 100
                || classId == 105 || classId == 104 || classId == 107
                || classId == 112 || classId == 115 || classId == 116) {
            // D-tier
            return 0.95;
        }
        if (classId == 89 || classId == 111) {
            // C-tier
            return 1.00;
        }
        if (classId == 90 || classId == 91 || classId == 99 || classId == 106) {
            // B-tier
            return 1.05;
        }
        if (classId == 113 || classId == 114) {
            // A-tier
            return 1.08;
        }
        if (classId == 92 || classId == 93 || classId == 94 || classId == 95
                || classId == 101 || classId == 102 || classId == 103
                || classId == 108 || classId == 109 || classId == 110) {
            // S-tier
            return 1.10;
        }

        return 1.0; // default baseline
    }

    public static class PlayerStats {
        public int charId;
        public int totalKills;
        public int totalDeaths;
        public int totalWins;
        public int totalLosses;
        public int flagsDestroyed;
        public int currentELO;
        public int highestELO;
        public int totalMatches;
        public java.sql.Timestamp lastActive;
    }
}
