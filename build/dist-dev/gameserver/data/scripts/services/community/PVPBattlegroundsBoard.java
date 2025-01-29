package services.community;

import l2ft.gameserver.Config;
import l2ft.gameserver.data.htm.HtmCache;
import l2ft.gameserver.handler.bbs.CommunityBoardManager;
import l2ft.gameserver.handler.bbs.ICommunityBoardHandler;
import l2ft.gameserver.model.Player;
import l2ft.gameserver.network.l2.s2c.ShowBoard;
import l2ft.gameserver.scripts.ScriptFile;
import l2ft.gameserver.utils.BbsUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// FBG references
import l2ft.gameserver.model.entity.events.impl.ForgottenBattlegroundsManager;
import l2ft.gameserver.model.entity.events.impl.ForgottenBattlegroundsEvent;
import l2ft.gameserver.scripts.services.community.ForgottenBattlegroundsBoard;

// IA references
import l2ft.gameserver.model.entity.events.impl.IslandAssaultManager;
import l2ft.gameserver.model.entity.events.impl.IslandAssaultEvent;
import l2ft.gameserver.scripts.services.community.IslandAssaultBattlegroundsBoard;

/**
 * PVPBattlegroundsBoard
 *
 * Single unified community board script for:
 * - Forgotten Battlegrounds (FBG)
 * - Island Assault (IA)
 *
 * The main “lobby” is shown via "_bbsPVPBattlegrounds" bypass,
 * using "bbs_pvp_bg_dashboard.htm".
 */
public class PVPBattlegroundsBoard implements ICommunityBoardHandler, ScriptFile {
    private static final Logger _log = LoggerFactory.getLogger(PVPBattlegroundsBoard.class);

    @Override
    public void onLoad() {
        CommunityBoardManager.getInstance().registerHandler(this);
        _log.info("PVPBattlegroundsBoard loaded: unified board for Forgotten BG + Island Assault.");
    }

    @Override
    public void onReload() {
        CommunityBoardManager.getInstance().removeHandler(this);
    }

    @Override
    public void onShutdown() {
        // no-op
    }

    @Override
    public String[] getBypassCommands() {
        return new String[] {
                "_bbsPVPBattlegrounds",

                // FBG
                "_bbsfbjoin_",
                "_bbsfbleave",
                "_bbsfbrespawn",
                "_bbsFBLive",

                // IA
                "_bbsIAjoin_",
                "_bbsIAleave",
                "_bbsIArespawn",
                "_bbsIALive"
        };
    }

    @Override
    public void onBypassCommand(Player player, String bypass) {
        if (player == null || bypass == null) {
            return;
        }

        if (bypass.equals("_bbsPVPBattlegrounds")) {
            showPVPBattlegroundsDashboard(player);
            return;
        }

        // === Forgotten BG ===
        if (bypass.startsWith("_bbsfbjoin_")) {
            handleFBJoin(player, bypass);
            showPVPBattlegroundsDashboard(player);
        } else if (bypass.startsWith("_bbsfbleave")) {
            ForgottenBattlegroundsManager.getInstance().requestLeaveEvent(player);
            showPVPBattlegroundsDashboard(player);
        } else if (bypass.startsWith("_bbsfbrespawn")) {
            ForgottenBattlegroundsManager.getInstance().requestRespawn(player);
            showPVPBattlegroundsDashboard(player);
        } else if (bypass.equals("_bbsFBLive")) {
            // let ForgottenBattlegroundsBoard handle the scoreboard
            String html = HtmCache.getInstance().getNotNull(Config.BBS_HOME_DIR + "bbs_forgotten_bg_live.htm", player);
            new ForgottenBattlegroundsBoard().showLiveStatusPage(player, html);
        }

        // === Island Assault ===
        else if (bypass.startsWith("_bbsIAjoin_")) {
            handleIAJoin(player, bypass);
            showPVPBattlegroundsDashboard(player);
        } else if (bypass.startsWith("_bbsIAleave")) {
            IslandAssaultManager.getInstance().requestLeaveEvent(player);
            showPVPBattlegroundsDashboard(player);
        } else if (bypass.startsWith("_bbsIArespawn")) {
            IslandAssaultManager.getInstance().requestRespawn(player);
            showPVPBattlegroundsDashboard(player);
        } else if (bypass.equals("_bbsIALive")) {
            // let IslandAssaultBattlegroundsBoard handle the scoreboard
            String html = HtmCache.getInstance().getNotNull(Config.BBS_HOME_DIR + "bbs_island_assault_bg_live.htm",
                    player);
            new IslandAssaultBattlegroundsBoard().showLiveStatusPage(player, html);
        }
    }

    @Override
    public void onWriteCommand(Player player, String bypass,
            String arg1, String arg2, String arg3, String arg4, String arg5) {
        // not used
    }

    /**
     * The main “lobby” page => "bbs_pvp_bg_dashboard.htm"
     */
    private void showPVPBattlegroundsDashboard(Player player) {
        String html = HtmCache.getInstance()
                .getNotNull(Config.BBS_HOME_DIR + "bbs_pvp_bg_dashboard.htm", player);

        // 1) Forgotten BG placeholders
        ForgottenBattlegroundsManager fbgMgr = ForgottenBattlegroundsManager.getInstance();
        boolean isInFBQueue = fbgMgr.isPlayerInQueue(player);
        ForgottenBattlegroundsEvent fbEvent = fbgMgr.getRunningEvent();
        boolean isInFBEvent = (fbEvent != null && fbEvent.isParticipant(player));

        String fbJoinButtons;
        if (isInFBEvent) {
            fbJoinButtons = "<button value=\"Leave FBG\" action=\"bypass _bbsfbleave\" width=150 height=30 "
                    + "back=\"L2UI_CT1.Button_DF_Down\" fore=\"L2UI_ct1.button_df\">";
        } else if (isInFBQueue) {
            fbJoinButtons = "<button value=\"Leave FBG Queue\" action=\"bypass _bbsfbleave\" width=150 height=30 "
                    + "back=\"L2UI_CT1.Button_DF_Down\" fore=\"L2UI_ct1.button_df\">";
        } else {
            fbJoinButtons = "<table border=0 cellpadding=2 cellspacing=2>"
                    + "  <tr>"
                    + "    <td><button value=\"Join Team A\" action=\"bypass _bbsfbjoin_1\" width=120 height=30 "
                    + "          back=\"L2UI_CT1.Button_DF_Down\" fore=\"L2UI_ct1.button_df\"></td>"
                    + "    <td><button value=\"Join Team B\" action=\"bypass _bbsfbjoin_2\" width=120 height=30 "
                    + "          back=\"L2UI_CT1.Button_DF_Down\" fore=\"L2UI_ct1.button_df\"></td>"
                    + "    <td><button value=\"First Available\" action=\"bypass _bbsfbjoin_0\" width=120 height=30 "
                    + "          back=\"L2UI_CT1.Button_DF_Down\" fore=\"L2UI_ct1.button_df\"></td>"
                    + "  </tr>"
                    + "</table>";
        }

        int fbgAcount = fbgMgr.getTeamAQueueCount();
        int fbgBcount = fbgMgr.getTeamBQueueCount();

        html = html.replace("%JOIN_BUTTONS%", fbJoinButtons);
        html = html.replace("%TEAM_A_COUNT%", String.valueOf(fbgAcount));
        html = html.replace("%TEAM_B_COUNT%", String.valueOf(fbgBcount));

        // 2) Island Assault placeholders
        IslandAssaultManager iaMgr = IslandAssaultManager.getInstance();
        boolean isInIAQueue = iaMgr.isPlayerInQueue(player);
        IslandAssaultEvent iaEvent = iaMgr.getRunningEvent();
        boolean isInIAEvent = (iaEvent != null && iaEvent.isParticipant(player));

        String iaJoinButtons;
        if (isInIAEvent) {
            iaJoinButtons = "<button value=\"Leave IA\" action=\"bypass _bbsIAleave\" width=150 height=30 "
                    + "back=\"L2UI_CT1.Button_DF_Down\" fore=\"L2UI_ct1.button_df\">";
        } else if (isInIAQueue) {
            iaJoinButtons = "<button value=\"Leave IA Queue\" action=\"bypass _bbsIAleave\" width=150 height=30 "
                    + "back=\"L2UI_CT1.Button_DF_Down\" fore=\"L2UI_ct1.button_df\">";
        } else {
            iaJoinButtons = "<table border=0 cellpadding=2 cellspacing=2>"
                    + "  <tr>"
                    + "    <td><button value=\"Join Team A\" action=\"bypass _bbsIAjoin_1\" width=120 height=30 "
                    + "          back=\"L2UI_CT1.Button_DF_Down\" fore=\"L2UI_ct1.button_df\"></td>"
                    + "    <td><button value=\"Join Team B\" action=\"bypass _bbsIAjoin_2\" width=120 height=30 "
                    + "          back=\"L2UI_CT1.Button_DF_Down\" fore=\"L2UI_ct1.button_df\"></td>"
                    + "    <td><button value=\"First Available\" action=\"bypass _bbsIAjoin_0\" width=120 height=30 "
                    + "          back=\"L2UI_CT1.Button_DF_Down\" fore=\"L2UI_ct1.button_df\"></td>"
                    + "  </tr>"
                    + "</table>";
        }

        int iaAcount = iaMgr.getTeamAQueueCount();
        int iaBcount = iaMgr.getTeamBQueueCount();

        html = html.replace("%JOIN_BUTTONS_IA%", iaJoinButtons);
        html = html.replace("%TEAM_A_COUNT_IA%", String.valueOf(iaAcount));
        html = html.replace("%TEAM_B_COUNT_IA%", String.valueOf(iaBcount));

        // Send final
        html = BbsUtil.htmlAll(html, player);
        ShowBoard.separateAndSend(html, player);
    }

    private void handleFBJoin(Player player, String bypass) {
        int chosenTeam = 0;
        try {
            chosenTeam = Integer.parseInt(bypass.substring("_bbsfbjoin_".length()));
        } catch (NumberFormatException e) {
            player.sendMessage("Invalid FBG team choice!");
        }
        ForgottenBattlegroundsManager.getInstance().registerPlayer(player, chosenTeam);
    }

    private void handleIAJoin(Player player, String bypass) {
        int chosenTeam = 0;
        try {
            chosenTeam = Integer.parseInt(bypass.substring("_bbsIAjoin_".length()));
        } catch (NumberFormatException e) {
            player.sendMessage("Invalid IA team choice!");
        }
        IslandAssaultManager.getInstance().registerPlayer(player, chosenTeam);
    }
}
