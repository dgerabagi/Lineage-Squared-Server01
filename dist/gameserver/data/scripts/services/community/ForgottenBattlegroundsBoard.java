package l2ft.gameserver.scripts.services.community;

import java.util.List;

import l2ft.gameserver.Config;
import l2ft.gameserver.data.htm.HtmCache;
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
                "_bbsForgottenBattlegroundsTab:"
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
                joinButtonsHtml = "<button value=\"Leave Battleground\" action=\"bypass _bbsfbleave\" width=150 height=30 back=\"L2UI_CT1.Button_DF_Down\" fore=\"L2UI_ct1.button_df\">";
            } else if (isInQueue) {
                joinButtonsHtml = "<button value=\"Leave Queue\" action=\"bypass _bbsfbleave\" width=150 height=30 back=\"L2UI_CT1.Button_DF_Down\" fore=\"L2UI_ct1.button_df\">";
            } else {
                joinButtonsHtml = "<table border=0 cellpadding=2 cellspacing=2>"
                        + "<tr><td><button value=\"Join Team A\" action=\"bypass _bbsfbjoin_1\" width=120 height=25 back=\"L2UI_CT1.Button_DF_Down\" fore=\"L2UI_ct1.button_df\"></td></tr>"
                        + "<tr><td><button value=\"Join Team B\" action=\"bypass _bbsfbjoin_2\" width=120 height=25 back=\"L2UI_CT1.Button_DF_Down\" fore=\"L2UI_ct1.button_df\"></td></tr>"
                        + "<tr><td><button value=\"First Available\" action=\"bypass _bbsfbjoin_0\" width=120 height=25 back=\"L2UI_CT1.Button_DF_Down\" fore=\"L2UI_ct1.button_df\"></td></tr>"
                        + "</table>";
            }

            // Replace placeholders
            html = html.replace("%JOIN_BUTTONS%", joinButtonsHtml);
            html = html.replace("%TEAM_A_COUNT%", String.valueOf(manager.getTeamAQueueCount()));
            html = html.replace("%TEAM_B_COUNT%", String.valueOf(manager.getTeamBQueueCount()));
            html = html.replace("%TEAM_A_PARTY_LIST%", getPartyListHtml(TeamType.BLUE));
            html = html.replace("%TEAM_B_PARTY_LIST%", getPartyListHtml(TeamType.RED));

            // Generate scoreboard HTML
            String scoreboard = getScoreboardHtml();
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
            // Handle tab switching if needed
            // For now, we can simply refresh the page (or do logic depending on tab)
            // The actual content might be the same since we are not fully implementing tabs
            // here
            onBypassCommand(player, "_bbsForgottenBattlegrounds");
        }
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
            html.append("<table width=240 border=0 bgcolor=\"22181A\">");
            html.append("<tr>");
            html.append("<td width=\"40\">Name:</td>");
            html.append("<td width=\"100\">").append(member.getName()).append("</td>");
            html.append("<td width=\"100\" align=right><button value=\"Info\" action=\"bypass -h player_info ")
                    .append(member.getObjectId())
                    .append("\" width=60 height=18 back=\"L2UI_CT1.Button_DF_Down\" fore=\"L2UI_ct1.button_df\"></td>");
            html.append("</tr><tr>");
            html.append("<td>Class:</td>");

            int classId = member.getActiveClassClassId().getId();
            html.append("<td>").append(getClassName(classId)).append("</td>");
            html.append("<td> </td>");
            html.append("</tr></table><br>");
        }

        return html.toString();
    }

    private String getScoreboardHtml() {
        ForgottenBattlegroundsEvent event = ForgottenBattlegroundsManager.getInstance().getRunningEvent();
        if (event == null) {
            return "No event currently running, no scoreboard available.";
        }

        List<Player> blueTeam = event.getTeamPlayers(TeamType.BLUE);
        List<Player> redTeam = event.getTeamPlayers(TeamType.RED);

        StringBuilder sb = new StringBuilder();
        sb.append("<table width=750 border=0 cellpadding=2 cellspacing=2 bgcolor=18191e>");
        sb.append(
                "<tr><td colspan=2 align=center><font color=\"66CCFF\">Blue Team</font></td><td colspan=2 align=center><font color=\"FF6666\">Red Team</font></td></tr>");
        sb.append(
                "<tr><td><font color=FFFF99>Player</font></td><td><font color=FFFF99>Stackclass</font></td><td><font color=FFFF99>Player</font></td><td><font color=FFFF99>Stackclass</font></td></tr>");

        int maxSize = Math.max(blueTeam.size(), redTeam.size());
        for (int i = 0; i < maxSize; i++) {
            Player blueP = i < blueTeam.size() ? blueTeam.get(i) : null;
            Player redP = i < redTeam.size() ? redTeam.get(i) : null;

            sb.append("<tr>");
            if (blueP != null) {
                // Retrieve primary/secondary classes from event maps
                Integer pcVal = event.getPrimaryClassMap().get(blueP.getObjectId());
                int pc = pcVal != null ? pcVal : 0;
                Integer scVal = event.getSecondaryClassMap().get(blueP.getObjectId());
                int sc = scVal != null ? scVal : 0;
                String stack = ClassAbbreviations.getAbbrev(pc) + "/" + ClassAbbreviations.getAbbrev(sc);
                sb.append("<td>").append(blueP.getName()).append("</td><td>").append(stack).append("</td>");
            } else {
                sb.append("<td></td><td></td>");
            }

            if (redP != null) {
                Integer pcVal = event.getPrimaryClassMap().get(redP.getObjectId());
                int pc = pcVal != null ? pcVal : 0;
                Integer scVal = event.getSecondaryClassMap().get(redP.getObjectId());
                int sc = scVal != null ? scVal : 0;
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
        // Adjust class names as desired. For now, a minimal mapping:
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

    @Override
    public void onWriteCommand(Player player, String bypass, String arg1, String arg2, String arg3, String arg4,
            String arg5) {
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
        // Cleanup if needed
    }
}
