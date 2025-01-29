package l2ft.gameserver.scripts.services.community;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import l2ft.commons.dbutils.DbUtils;
import l2ft.gameserver.Config;
import l2ft.gameserver.data.htm.HtmCache;
import l2ft.gameserver.database.DatabaseFactory;
import l2ft.gameserver.handler.bbs.CommunityBoardManager;
import l2ft.gameserver.handler.bbs.ICommunityBoardHandler;
import l2ft.gameserver.model.Player;
import l2ft.gameserver.model.base.TeamType;
import l2ft.gameserver.model.entity.events.impl.IslandAssaultEvent;
import l2ft.gameserver.model.entity.events.impl.IslandAssaultManager;
import l2ft.gameserver.network.l2.s2c.ShowBoard;
import l2ft.gameserver.scripts.ScriptFile;
import l2ft.gameserver.utils.BbsUtil;
import l2ft.gameserver.utils.ClassAbbreviations;

/**
 * IslandAssaultBattlegroundsBoard
 * 
 * Community Board handler that focuses on the Island Assault scoreboard ("Live
 * Status")
 * and minimal placeholders if needed.
 * PVPBattlegroundsBoard calls into this class to show the scoreboard.
 */
public class IslandAssaultBattlegroundsBoard implements ICommunityBoardHandler, ScriptFile {
    @Override
    public String[] getBypassCommands() {
        // You can handle _bbsIslandAssault, _bbsIAjoin_, etc. here if desired.
        return new String[] {
                "_bbsIslandAssault",
                "_bbsIAjoin_",
                "_bbsIAleave",
                "_bbsIArespawn",
                "_bbsIALive"
        };
    }

    @Override
    public void onBypassCommand(Player player, String bypass) {
        IslandAssaultManager manager = IslandAssaultManager.getInstance();
        IslandAssaultEvent event = manager.getRunningEvent();

        if (bypass.startsWith("_bbsIslandAssault")) {
            // Minimal placeholder or landing page:
            String html = "<html><body>Island Assault board is not fully implemented yet.</body></html>";
            ShowBoard.separateAndSend(html, player);
            return;
        }

        // Normally, PVPBattlegroundsBoard does the main join/leave logic,
        // so we only handle scoreboard or fallback logic here.
    }

    /**
     * This method is called by PVPBattlegroundsBoard to display
     * the live scoreboard for Island Assault.
     */
    public void showLiveStatusPage(Player player, String baseHtml) {
        IslandAssaultEvent iaEvent = IslandAssaultManager.getInstance().getRunningEvent();
        if (iaEvent == null) {
            baseHtml = baseHtml.replace(
                    "%BLUE_TEAM_ROWS%",
                    "<table bgcolor=003377 height=25>" +
                            "<tr><td colspan=6>No Island Assault is currently running.</td></tr></table>");

            baseHtml = baseHtml.replace(
                    "%RED_TEAM_ROWS%",
                    "<table bgcolor=440000 height=25>" +
                            "<tr><td colspan=6>No Island Assault is currently running.</td></tr></table>");

            baseHtml = BbsUtil.htmlAll(baseHtml, player);
            ShowBoard.separateAndSend(baseHtml, player);
            return;
        }

        // If event is running, proceed with the normal code ...
        int matchId = fetchCurrentMatchIdFromLiveStatus();
        if (matchId <= 0) {
            // If we don't get a valid match ID, fallback with "No players"
            baseHtml = baseHtml.replace("%BLUE_TEAM_ROWS%", "<table><tr><td>No Blue players</td></tr></table>");
            baseHtml = baseHtml.replace("%RED_TEAM_ROWS%", "<table><tr><td>No Red players</td></tr></table>");
            baseHtml = BbsUtil.htmlAll(baseHtml, player);
            ShowBoard.separateAndSend(baseHtml, player);
            return;
        }

        // 2) Get the lists of players in the event from memory
        List<Player> bluePlayers = iaEvent.getTeamPlayers(TeamType.BLUE);
        List<Player> redPlayers = iaEvent.getTeamPlayers(TeamType.RED);

        // 3) Query the DB for stats in iab_live_players
        Map<Integer, PlayerStats> liveStats = fetchLivePlayerStats(matchId);

        // 4) Build the scoreboard rows
        String blueHtml = buildTeamRows(bluePlayers, true, liveStats, iaEvent);
        String redHtml = buildTeamRows(redPlayers, false, liveStats, iaEvent);

        // 5) Inject into the HTML placeholders
        baseHtml = baseHtml.replace("%BLUE_TEAM_ROWS%", blueHtml);
        baseHtml = baseHtml.replace("%RED_TEAM_ROWS%", redHtml);

        baseHtml = BbsUtil.htmlAll(baseHtml, player);
        ShowBoard.separateAndSend(baseHtml, player);
    }

    // -------------------------------------------------------------------------
    // Scoreboard logic & DB queries
    // -------------------------------------------------------------------------

    /**
     * Container for an Island Assault player's live stats, fetched from DB.
     */
    private static class PlayerStats {
        int currentELO;
        int kills;
        int deaths;
        int flagsDestroyed;
    }

    /**
     * Reads iab_live_status to get the currentMatchId, if eventLive=1.
     */
    private int fetchCurrentMatchIdFromLiveStatus() {
        int matchId = 0;
        Connection con = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            con = DatabaseFactory.getInstance().getConnection();

            // The correct table is iab_live_status,
            // and the columns are (id, eventLive, currentMatchId, ...).
            ps = con.prepareStatement("SELECT eventLive, currentMatchId FROM iab_live_status LIMIT 1");
            rs = ps.executeQuery();
            if (rs.next()) {
                if (rs.getInt("eventLive") == 1) {
                    matchId = rs.getInt("currentMatchId");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            DbUtils.closeQuietly(con, ps, rs);
        }
        return matchId;
    }

    /**
     * Reads iab_live_players to fetch each participant’s ELO, K/D, flags, etc.
     * 
     * Table iab_live_players has columns:
     * matchId, charId, team, currentELO, kills, deaths,
     * primaryClassId, secondaryClassId, flagsDestroyed, lastUpdate
     */
    private Map<Integer, PlayerStats> fetchLivePlayerStats(int matchId) {
        Map<Integer, PlayerStats> map = new HashMap<Integer, PlayerStats>();
        Connection con = null;
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            con = DatabaseFactory.getInstance().getConnection();

            // We'll fetch the relevant columns from iab_live_players
            ps = con.prepareStatement(
                    "SELECT charId, currentELO, kills, deaths, flagsDestroyed "
                            + "FROM iab_live_players "
                            + "WHERE matchId = ?");
            ps.setInt(1, matchId);
            rs = ps.executeQuery();

            while (rs.next()) {
                int charId = rs.getInt("charId");

                PlayerStats st = new PlayerStats();
                st.currentELO = rs.getInt("currentELO");
                st.kills = rs.getInt("kills");
                st.deaths = rs.getInt("deaths");
                st.flagsDestroyed = rs.getInt("flagsDestroyed");

                map.put(charId, st);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            DbUtils.closeQuietly(con, ps, rs);
        }

        return map;
    }

    /**
     * Builds
     * <table>
     * rows for either BLUE or RED side, based on the event memory
     * plus the DB stats (ELO, kills, etc.).
     */
    private String buildTeamRows(
            List<Player> players,
            boolean isBlue,
            Map<Integer, PlayerStats> statsMap,
            IslandAssaultEvent iaEvent) {
        if (players == null || players.isEmpty()) {
            if (isBlue) {
                return "<table bgcolor=003377 height=25><tr><td>No Blue Players</td></tr></table>";
            } else {
                return "<table bgcolor=440000 height=25><tr><td>No Red Players</td></tr></table>";
            }
        }

        StringBuilder sb = new StringBuilder();
        // We can alternate row colors for each team if desired:
        String[] blueColors = { "001122", "003344" };
        String[] redColors = { "220000", "440000" };

        int rowIndex = 0;
        for (Player p : players) {
            if (p == null)
                continue;

            int charId = p.getObjectId();
            PlayerStats st = statsMap.get(charId);

            // If not found in DB, default to 2000 ELO and 0 stats
            int elo = (st != null ? st.currentELO : 2000);
            int kills = (st != null ? st.kills : 0);
            int deaths = (st != null ? st.deaths : 0);
            int flags = (st != null ? st.flagsDestroyed : 0);

            // Show "ClassAbbrev1/ClassAbbrev2"
            Integer pc = iaEvent.getPrimaryClassMap().get(charId);
            if (pc == null)
                pc = 0;
            Integer sc = iaEvent.getSecondaryClassMap().get(charId);
            if (sc == null)
                sc = 0;
            String combo = ClassAbbreviations.getAbbrev(pc)
                    + "/"
                    + ClassAbbreviations.getAbbrev(sc);

            String bg = isBlue
                    ? blueColors[rowIndex % blueColors.length]
                    : redColors[rowIndex % redColors.length];

            sb.append("<table bgcolor=").append(bg).append(" height=25><tr>");
            sb.append("<td width=120>").append(p.getName()).append("</td>");
            sb.append("<td width=100>").append(combo).append("</td>");
            sb.append("<td width=60>").append(elo).append("</td>");
            sb.append("<td width=60>").append(kills).append("</td>");
            sb.append("<td width=80>").append(deaths).append("</td>");
            sb.append("<td width=80>").append(flags).append("</td>");
            sb.append("</tr></table>");

            rowIndex++;
        }
        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // Implementation of ICommunityBoardHandler & ScriptFile
    // -------------------------------------------------------------------------
    @Override
    public void onWriteCommand(Player player, String bypass,
            String arg1, String arg2, String arg3, String arg4, String arg5) {
        // Not used here
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
        // no-op
    }
}
