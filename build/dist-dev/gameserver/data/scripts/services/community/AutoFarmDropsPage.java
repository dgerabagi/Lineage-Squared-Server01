package services.community;

import java.util.StringTokenizer;

import l2ft.gameserver.Config;
import l2ft.gameserver.data.htm.HtmCache;
import l2ft.gameserver.handler.bbs.CommunityBoardManager;
import l2ft.gameserver.handler.bbs.ICommunityBoardHandler;
import l2ft.gameserver.model.Player;
import l2ft.gameserver.network.l2.s2c.ShowBoard;
import l2ft.gameserver.scripts.Functions;
import l2ft.gameserver.scripts.ScriptFile;
import l2ft.gameserver.utils.BbsUtil;

// ADD THIS:
import l2ft.gameserver.autofarm.DropTrackerData;
import l2ft.gameserver.autofarm.AutoFarmEngine;
import l2ft.gameserver.autofarm.AutoFarmState;

public class AutoFarmDropsPage extends Functions implements ScriptFile, ICommunityBoardHandler {
    private static final String DROPS_HTML = "autofarm/bbs_autoFarmDrops.htm";

    @Override
    public String[] getBypassCommands() {
        return new String[] { "_bbsautofarmdrops" };
    }

    @Override
    public void onBypassCommand(Player player, String bypass) {
        if (player == null)
            return;

        StringTokenizer st = new StringTokenizer(bypass, " ");
        // e.g. _bbsautofarmdrops show
        // _bbsautofarmdrops reset
        String cmd = st.hasMoreTokens() ? st.nextToken() : "show";
        String subCmd = st.hasMoreTokens() ? st.nextToken().toLowerCase() : "show";

        AutoFarmState state = AutoFarmEngine.getInstance().getOrCreateState(player);
        if (state == null)
            return;

        if ("reset".equals(subCmd)) {
            DropTrackerData.get(state).reset();
            player.sendMessage("Drops tracker has been reset.");
            showDropsPage(player, state);
        } else {
            // default "show"
            showDropsPage(player, state);
        }
    }

    private void showDropsPage(Player player, AutoFarmState state) {
        String html = HtmCache.getInstance().getNotNull(Config.BBS_HOME_DIR + DROPS_HTML, player);
        DropTrackerData drops = DropTrackerData.get(state);

        String tableRows = drops.buildHtmlRows();
        html = html.replace("%DROPS_ROWS%", tableRows);

        html = BbsUtil.htmlAll(html, player);
        ShowBoard.separateAndSend(html, player);
    }

    @Override
    public void onWriteCommand(Player player, String bypass, String arg1, String arg2,
            String arg3, String arg4, String arg5) {
        // no-op
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
