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
import l2ft.gameserver.model.entity.events.impl.ForgottenBattlegroundsEvent;
import l2ft.gameserver.model.entity.events.impl.ForgottenBattlegroundsManager;
import l2ft.gameserver.network.l2.s2c.ShowBoard;
import l2ft.gameserver.scripts.ScriptFile;
import l2ft.gameserver.utils.BbsUtil;
import l2ft.gameserver.utils.ClassAbbreviations;

public class ForgottenBattlegroundsBoard implements ICommunityBoardHandler, ScriptFile {

    @Override
    public String[] getBypassCommands() {
        return new String[] {
                "_bbsForgottenBattlegrounds",
                "_bbsfbjoin_",
                "_bbsfbleave",
                "_bbsfbrespawn",
                "_bbsForgottenBattlegroundsTab:",
                "_bbsFBLive"
        };
    }

    @Override
    public void onBypassCommand(Player player, String bypass) {
        ForgottenBattlegroundsManager manager = ForgottenBattlegroundsManager.getInstance();
        ForgottenBattlegroundsEvent event = manager.getRunningEvent();

        if (bypass.startsWith("_bbsForgottenBattlegrounds")) {
            String html = HtmCache.getInstance().getNotNull(Config.BBS_HOME_DIR + "bbs_forgotten_bg_dashboard.htm",
                    player);

            boolean isInQueue = manager.isPlayerInQueue(player);
            boolean isInEvent = (event != null && event.isParticipant(player));

            String joinButtonsHtml;
            if (isInEvent) {
                joinButtonsHtml = "<button value=\"Leave Battleground\" action=\"bypass _bbsfbleave\" "
                        + "width=150 height=30 back=\"L2UI_CT1.Button_DF_Down\" fore=\"L2UI_ct1.button_df\">";
            } else if (isInQueue) {
                joinButtonsHtml = "<button value=\"Leave Queue\" action=\"bypass _bbsfbleave\" "
                        + "width=150 height=30 back=\"L2UI_CT1.Button_DF_Down\" fore=\"L2UI_ct1.button_df\">";
            } else {
                // Provide 3 join buttons in a row
                joinButtonsHtml = "<table border=0 cellpadding=2 cellspacing=2>"
                        + " <tr>"
                        + "   <td><button value=\"Join Team A\" action=\"bypass _bbsfbjoin_1\" width=120 height=30 "
                        + "        back=\"L2UI_CT1.Button_DF_Down\" fore=\"L2UI_ct1.button_df\"></td>"
                        + "   <td><button value=\"Join Team B\" action=\"bypass _bbsfbjoin_2\" width=120 height=30 "
                        + "        back=\"L2UI_CT1.Button_DF_Down\" fore=\"L2UI_ct1.button_df\"></td>"
                        + "   <td><button value=\"First Available\" action=\"bypass _bbsfbjoin_0\" width=120 height=30 "
                        + "        back=\"L2UI_CT1.Button_DF_Down\" fore=\"L2UI_ct1.button_df\"></td>"
                        + " </tr>"
                        + "</table>";
            }

            html = html.replace("%JOIN_BUTTONS%", joinButtonsHtml);
            html = html.replace("%TEAM_A_COUNT%", String.valueOf(manager.getTeamAQueueCount()));
            html = html.replace("%TEAM_B_COUNT%", String.valueOf(manager.getTeamBQueueCount()));
            html = html.replace("%TEAM_A_PARTY_LIST%", getPartyListHtml(TeamType.BLUE));
            html = html.replace("%TEAM_B_PARTY_LIST%", getPartyListHtml(TeamType.RED));

            // Minimal scoreboard snippet
            String scoreboard = getScoreboardHtmlMinimal();
            html = html.replace("%SCOREBOARD%", scoreboard);

            html = BbsUtil.htmlAll(html, player);
            ShowBoard.separateAndSend(html, player);
        } else if (bypass.startsWith("_bbsfbjoin_")) {
            String teamArg = bypass.substring("_bbsfbjoin_".length());
            int teamChoice = 0;
            try {
                teamChoice = Integer.parseInt(teamArg);
            } catch (NumberFormatException e) {
                player.sendMessage("Invalid team choice.");
                return;
            }

            manager.registerPlayer(player, teamChoice);
            player.sendMessage("Your request to join has been registered.");
            onBypassCommand(player, "_bbsForgottenBattlegrounds");
        } else if (bypass.startsWith("_bbsfbleave")) {
            manager.requestLeaveEvent(player);
            onBypassCommand(player, "_bbsForgottenBattlegrounds");
        } else if (bypass.startsWith("_bbsForgottenBattlegroundsTab:")) {
            // Refresh page if needed
            onBypassCommand(player, "_bbsForgottenBattlegrounds");
        } else if (bypass.equals("_bbsFBLive")) {
            String html = HtmCache.getInstance().getNotNull(Config.BBS_HOME_DIR + "bbs_forgotten_bg_live.htm", player);

            // Similar logic as showLiveStatusPage:
            int currentMatchId = fetchCurrentMatchIdFromLiveStatus();
            if (event == null || currentMatchId <= 0) {
                // No event
                String blueTeamRows = "<table bgcolor=003377 height=25><tr><td colspan=6>No Blue Players</td></tr></table>";
                String redTeamRows = "<table bgcolor=440000 height=25><tr><td colspan=6>No Red Players</td></tr></table>";
                html = html.replace("%BLUE_TEAM_ROWS%", blueTeamRows);
                html = html.replace("%RED_TEAM_ROWS%", redTeamRows);
            } else {
                List<Player> blueTeam = event.getTeamPlayers(TeamType.BLUE);
                List<Player> redTeam = event.getTeamPlayers(TeamType.RED);

                Map<Integer, PlayerStats> liveStats = fetchLivePlayerStats(currentMatchId);

                String blueTeamRows = buildTeamRows(blueTeam, true, liveStats, event);
                String redTeamRows = buildTeamRows(redTeam, false, liveStats, event);

                html = html.replace("%BLUE_TEAM_ROWS%", blueTeamRows);
                html = html.replace("%RED_TEAM_ROWS%", redTeamRows);
            }

            html = BbsUtil.htmlAll(html, player);
            ShowBoard.separateAndSend(html, player);
        }
    }

    // -------------------------------------------------------------------------
    // Additional logic for scoreboard data
    // -------------------------------------------------------------------------
    private static class PlayerStats {
        int currentELO;
        int kills;
        int deaths;
        int flagsDestroyed;
    }

    private int fetchCurrentMatchIdFromLiveStatus() {
        int matchId = 0;
        Connection con = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            con = DatabaseFactory.getInstance().getConnection();
            ps = con.prepareStatement("SELECT eventLive, currentMatchId FROM fbg_live_status LIMIT 1");
            rs = ps.executeQuery();
            if (rs.next()) {
                int eventLive = rs.getInt("eventLive");
                if (eventLive == 1) {
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

    private Map<Integer, PlayerStats> fetchLivePlayerStats(int matchId) {
        Map<Integer, PlayerStats> map = new HashMap<Integer, PlayerStats>();
        Connection con = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            con = DatabaseFactory.getInstance().getConnection();
            ps = con.prepareStatement(
                    "SELECT charId, currentELO, kills, deaths, flagsDestroyed FROM fbg_live_players WHERE matchId=?");
            ps.setInt(1, matchId);
            rs = ps.executeQuery();
            while (rs.next()) {
                int charId = rs.getInt("charId");
                PlayerStats stats = new PlayerStats();
                stats.currentELO = rs.getInt("currentELO");
                stats.kills = rs.getInt("kills");
                stats.deaths = rs.getInt("deaths");
                stats.flagsDestroyed = rs.getInt("flagsDestroyed");
                map.put(charId, stats);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            DbUtils.closeQuietly(con, ps, rs);
        }
        return map;
    }

    private String buildTeamRows(List<Player> players, boolean isBlue,
            Map<Integer, PlayerStats> liveStatsMap,
            ForgottenBattlegroundsEvent event) {
        if (players == null || players.isEmpty()) {
            String color = isBlue ? "003377" : "440000";
            return "<table bgcolor=" + color + " height=25><tr><td colspan=6>No Players</td></tr></table>";
        }

        StringBuilder sb = new StringBuilder();
        String[] blueColors = { "003377", "001122" };
        String[] redColors = { "440000", "220000" };

        int rowCount = 0;
        for (Player p : players) {
            int colorIndex = rowCount % 2;
            String bgColor = isBlue ? blueColors[colorIndex] : redColors[colorIndex];

            // Possibly store primary/secondary class
            Integer pc = event.getPrimaryClassMap().get(p.getObjectId());
            if (pc == null)
                pc = 0;
            Integer sc = event.getSecondaryClassMap().get(p.getObjectId());
            if (sc == null)
                sc = 0;
            String stack = ClassAbbreviations.getAbbrev(pc) + "/" + ClassAbbreviations.getAbbrev(sc);

            PlayerStats stats = liveStatsMap.get(p.getObjectId());
            int elo = (stats != null) ? stats.currentELO : 2000;
            int kills = (stats != null) ? stats.kills : 0;
            int deaths = (stats != null) ? stats.deaths : 0;
            int flags = (stats != null) ? stats.flagsDestroyed : 0;

            sb.append("<table bgcolor=").append(bgColor).append(" height=25><tr>");
            sb.append("<td width=120>").append(p.getName()).append("</td>");
            sb.append("<td width=100>").append(stack).append("</td>");
            sb.append("<td width=60>").append(elo).append("</td>");
            sb.append("<td width=60>").append(kills).append("</td>");
            sb.append("<td width=80>").append(deaths).append("</td>");
            sb.append("<td width=80>").append(flags).append("</td>");
            sb.append("</tr></table>");

            rowCount++;
        }

        return sb.toString();
    }

    private String getPartyListHtml(TeamType teamType) {
        ForgottenBattlegroundsEvent event = ForgottenBattlegroundsManager.getInstance().getRunningEvent();
        if (event == null) {
            return "Event not running.";
        }

        List<Player> teamMembers = event.getTeamPlayers(teamType);
        if (teamMembers.isEmpty()) {
            return "No players on this team yet.";
        }

        StringBuilder html = new StringBuilder();
        for (Player member : teamMembers) {
            html.append("<table width=240 border=0>")
                    .append("<tr>")
                    .append("<td>Name:</td>")
                    .append("<td>").append(member.getName()).append("</td>")
                    .append("<td align=right>")
                    .append("<button value=\"Info\" action=\"bypass -h player_info ").append(member.getObjectId())
                    .append("\" ")
                    .append("width=60 height=18 back=\"L2UI_CT1.Button_DF_Down\" fore=\"L2UI_ct1.button_df\"></button>")
                    .append("</td>")
                    .append("</tr><tr>")
                    .append("<td>Class:</td>")
                    .append("<td>").append(getClassName(member.getActiveClassClassId().getId())).append("</td>")
                    .append("<td></td>")
                    .append("</tr></table><br>");
        }
        return html.toString();
    }

    private String getScoreboardHtmlMinimal() {
        ForgottenBattlegroundsEvent event = ForgottenBattlegroundsManager.getInstance().getRunningEvent();
        if (event == null) {
            return "No event currently running, no scoreboard available.";
        }

        List<Player> blueTeam = event.getTeamPlayers(TeamType.BLUE);
        List<Player> redTeam = event.getTeamPlayers(TeamType.RED);

        StringBuilder sb = new StringBuilder();
        sb.append("<table width=750 border=0 cellpadding=2 cellspacing=2>");
        sb.append("<tr>");
        sb.append("<td colspan=2 align=center>Blue Team</td>");
        sb.append("<td colspan=2 align=center>Red Team</td>");
        sb.append("</tr>");
        sb.append("<tr>");
        sb.append("<td>Player</td><td>Stackclass</td>");
        sb.append("<td>Player</td><td>Stackclass</td>");
        sb.append("</tr>");

        int maxSize = Math.max(blueTeam.size(), redTeam.size());
        for (int i = 0; i < maxSize; i++) {
            Player blueP = i < blueTeam.size() ? blueTeam.get(i) : null;
            Player redP = i < redTeam.size() ? redTeam.get(i) : null;

            sb.append("<tr>");
            if (blueP != null) {
                Integer pc = event.getPrimaryClassMap().get(blueP.getObjectId());
                if (pc == null)
                    pc = 0;
                Integer sc = event.getSecondaryClassMap().get(blueP.getObjectId());
                if (sc == null)
                    sc = 0;
                String stack = ClassAbbreviations.getAbbrev(pc) + "/" + ClassAbbreviations.getAbbrev(sc);
                sb.append("<td>").append(blueP.getName()).append("</td><td>").append(stack).append("</td>");
            } else {
                sb.append("<td></td><td></td>");
            }

            if (redP != null) {
                Integer pc = event.getPrimaryClassMap().get(redP.getObjectId());
                if (pc == null)
                    pc = 0;
                Integer sc = event.getSecondaryClassMap().get(redP.getObjectId());
                if (sc == null)
                    sc = 0;
                String stack = ClassAbbreviations.getAbbrev(pc) + "/" + ClassAbbreviations.getAbbrev(sc);
                sb.append("<td>").append(redP.getName()).append("</td><td>").append(stack).append("</td>");
            } else {
                sb.append("<td></td><td></td>");
            }
            sb.append("</tr>");
        }
        sb.append("</table>");
        return sb.toString();
    }

    private String getClassName(int classId) {
        switch (classId) {
            case 97:
                return "Cardinal";
            case 105:
            case 112:
                return "Recharger";
            case 100:
                return "SwordSinger";
            case 107:
                return "Bladedancer";
            default:
                return "Fighter";
        }
    }

    // -------------------------------------------------------------------------
    // This method is newly added so PVPBattlegroundsBoard can directly call it:
    // -------------------------------------------------------------------------
    public void showLiveStatusPage(Player player, String baseHtml)
    {
        ForgottenBattlegroundsEvent event = ForgottenBattlegroundsManager.getInstance().getRunningEvent();
        if (event == null)
        {
            // Reuse the same 'baseHtml' so we keep the unified header
            // but show a note in place of the scoreboard rows.
            baseHtml = baseHtml.replace(
                "%BLUE_TEAM_ROWS%",
                "<table bgcolor=003377 height=25>" +
                "<tr><td colspan=6>No Forgotten Battleground is running.</td></tr></table>");
    
            baseHtml = baseHtml.replace(
                "%RED_TEAM_ROWS%",
                "<table bgcolor=440000 height=25>" +
                "<tr><td colspan=6>No Forgotten Battleground is running.</td></tr></table>");
    
            baseHtml = BbsUtil.htmlAll(baseHtml, player);
            ShowBoard.separateAndSend(baseHtml, player);
            return;
        }
    
        // If we *do* have an event running, do the existing logic:
        int matchId = fetchCurrentMatchIdFromLiveStatus();
        if (matchId <= 0)
        {
            baseHtml = baseHtml.replace("%BLUE_TEAM_ROWS%",
                "<table><tr><td colspan=6>No Blue Players</td></tr></table>");
            baseHtml = baseHtml.replace("%RED_TEAM_ROWS%",
                "<table><tr><td colspan=6>No Red Players</td></tr></table>");
            baseHtml = BbsUtil.htmlAll(baseHtml, player);
            ShowBoard.separateAndSend(baseHtml, player);
            return;
        }

        // Build scoreboard from memory + DB
        List<Player> blueTeam = event.getTeamPlayers(TeamType.BLUE);
        List<Player> redTeam = event.getTeamPlayers(TeamType.RED);

        Map<Integer, PlayerStats> liveStats = fetchLivePlayerStats(matchId);

        String blueRows = buildTeamRows(blueTeam, true, liveStats, event);
        String redRows = buildTeamRows(redTeam, false, liveStats, event);

        baseHtml = baseHtml.replace("%BLUE_TEAM_ROWS%", blueRows);
        baseHtml = baseHtml.replace("%RED_TEAM_ROWS%", redRows);

        baseHtml = BbsUtil.htmlAll(baseHtml, player);
        ShowBoard.separateAndSend(baseHtml, player);
    }

    // -------------------------------------------------------------------------
    // Implementation of ICommunityBoardHandler, ScriptFile
    // -------------------------------------------------------------------------
    @Override
    public void onWriteCommand(Player player, String bypass,
            String arg1, String arg2, String arg3, String arg4, String arg5) {
        // Unused
    }

    @Override
    public void onLoad() {
        CommunityBoardManager.getInstance().registerHandler(this);
    }

    @Override
    public void onReload() {
        // Nothing
    }

    @Override
    public void onShutdown() {
        // Nothing special
    }
}
