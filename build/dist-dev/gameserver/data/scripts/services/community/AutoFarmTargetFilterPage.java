package services.community;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;

import l2ft.gameserver.Config;
import l2ft.gameserver.data.htm.HtmCache;
import l2ft.gameserver.handler.bbs.CommunityBoardManager;
import l2ft.gameserver.handler.bbs.ICommunityBoardHandler;
import l2ft.gameserver.model.Creature;
import l2ft.gameserver.model.Player;
import l2ft.gameserver.model.World;
import l2ft.gameserver.model.instances.MonsterInstance;
import l2ft.gameserver.network.l2.s2c.ShowBoard;
import l2ft.gameserver.scripts.Functions;
import l2ft.gameserver.scripts.ScriptFile;
import l2ft.gameserver.utils.BbsUtil;

import l2ft.gameserver.autofarm.AutoFarmEngine;
import l2ft.gameserver.autofarm.AutoFarmState;
import l2ft.gameserver.autofarm.ESearchType;
import l2ft.gameserver.autofarm.TargetFilterData;
import l2ft.gameserver.dao.CommunityTargetsDAO;
import l2ft.gameserver.model.ManageBbsTargets;

/**
 * This script handles the "AutoFarm Target Filter" page in the community board.
 * (a) We omit "(ID:####)" from lines shown to the user,
 * (b) We make toggled NPC IDs persistent by saving to DB on toggles,
 * (c) We load from DB at server start so the user's ignore set is retained.
 */
public class AutoFarmTargetFilterPage extends Functions
        implements ScriptFile, ICommunityBoardHandler {
    private static final String FILTER_HTML = "autofarm/bbs_autoFarmFilter.htm";

    @Override
    public String[] getBypassCommands() {
        return new String[] { "_bbsautofarmfilter" };
    }

    @Override
    public void onBypassCommand(Player player, String bypass) {
        if (player == null) {
            return;
        }

        StringTokenizer st = new StringTokenizer(bypass, " ");
        String cmd = st.hasMoreTokens() ? st.nextToken() : "show";
        String subCmd = st.hasMoreTokens() ? st.nextToken().toLowerCase() : "show";

        AutoFarmState farmState = AutoFarmEngine.getInstance().getOrCreateState(player);
        if (farmState == null) {
            return;
        }

        TargetFilterData tfData = TargetFilterData.get(farmState);

        if ("reset".equals(subCmd)) {
            // Wipe the user's ignore set in memory and in DB
            tfData.clear();
            ManageBbsTargets.getInstance().clearIgnored(player);
            CommunityTargetsDAO.getInstance().savePlayerIgnoreSet(player.getObjectId(), new ArrayList<Integer>());

            player.sendMessage("Target filter reset: attacking everything by default.");
            showFilterPage(player, farmState);

        } else if ("toggle".equals(subCmd)) {
            if (!st.hasMoreTokens()) {
                // No npcId was specified
                showFilterPage(player, farmState);
                return;
            }
            String npcIdStr = st.nextToken();
            try {
                int npcId = Integer.parseInt(npcIdStr);
                boolean wasIgnored = tfData.isIgnored(npcId);

                // Toggle in memory
                if (wasIgnored) {
                    tfData.unignore(npcId);
                    ManageBbsTargets.getInstance().unignore(player.getObjectId(), npcId);
                } else {
                    tfData.ignore(npcId);
                    ManageBbsTargets.getInstance().ignore(player.getObjectId(), npcId);
                }

                // Convert from Set<Integer> to List<Integer> before passing to DAO:
                Set<Integer> ignoredSet = ManageBbsTargets.getInstance().getIgnoredForChar(player.getObjectId());
                List<Integer> allIgnored = new ArrayList<Integer>(ignoredSet);

                // Now persist:
                CommunityTargetsDAO.getInstance().savePlayerIgnoreSet(player.getObjectId(), allIgnored);

            } catch (NumberFormatException e) {
                player.sendMessage("Invalid NPC ID: " + npcIdStr);
            }

            showFilterPage(player, farmState);

        } else {
            // "show" or fallback
            showFilterPage(player, farmState);
        }
    }

    /**
     * Renders the main filter HTML page with the monster ignore/allow lines.
     */
    private void showFilterPage(Player player, AutoFarmState state) {
        if (player == null) {
            return;
        }

        String html = HtmCache.getInstance().getNotNull(Config.BBS_HOME_DIR + FILTER_HTML, player);
        String rows = buildFilterRows(player, state);

        html = html.replace("%FILTER_LIST%", rows);
        html = BbsUtil.htmlAll(html, player);

        ShowBoard.separateAndSend(html, player);
    }

    /**
     * Collect unique nearby monster IDs, separate them into ignored / not-ignored,
     * and produce the table rows for each set.
     *
     * We do not show "ID: ####" in the text; we just show "Lv. XX Name".
     */
    private String buildFilterRows(Player player, AutoFarmState state) {
        if (player == null || state == null) {
            return "<tr><td>No player/state data.</td></tr>";
        }

        TargetFilterData tfData = TargetFilterData.get(state);

        ESearchType stype = state.getSearchType();
        int range = (stype != null && stype.getRange() > 0) ? stype.getRange() : 2000;

        List<Creature> around = World.getAroundCharacters(player);
        if (around == null || around.isEmpty()) {
            return "<tr><td>No monsters found nearby.</td></tr>";
        }

        // Gather unique NPC IDs (avoid duplicates).
        Map<Integer, MonsterInstance> uniqueMap = new HashMap<Integer, MonsterInstance>();
        for (Creature c : around) {
            if (!c.isMonster() || c.isDead()) {
                continue;
            }
            if (player.getDistance(c) > range) {
                continue;
            }
            MonsterInstance mon = (MonsterInstance) c;
            int npcId = mon.getNpcId();

            if (!uniqueMap.containsKey(npcId)) {
                uniqueMap.put(npcId, mon);
            }
        }

        if (uniqueMap.isEmpty()) {
            return "<tr><td>No monsters in range.</td></tr>";
        }

        // Split into not-ignored (green) vs. ignored (red)
        List<MonsterInstance> greenList = new ArrayList<MonsterInstance>();
        List<MonsterInstance> redList = new ArrayList<MonsterInstance>();

        for (MonsterInstance mon : uniqueMap.values()) {
            if (tfData.isIgnored(mon.getNpcId())) {
                redList.add(mon);
            } else {
                greenList.add(mon);
            }
        }

        // Sort them by ID, just to keep the list stable
        Comparator<MonsterInstance> comp = new Comparator<MonsterInstance>() {
            @Override
            public int compare(MonsterInstance a, MonsterInstance b) {
                return Integer.compare(a.getNpcId(), b.getNpcId());
            }
        };
        Collections.sort(greenList, comp);
        Collections.sort(redList, comp);

        StringBuilder sb = new StringBuilder();
        final int MAX_LINES = 100;
        int count = 0;

        // Add "not ignored" lines first
        for (MonsterInstance mon : greenList) {
            if (count >= MAX_LINES) {
                break;
            }
            sb.append(formatLine(mon, false));
            count++;
        }

        // Then the "ignored" lines
        for (MonsterInstance mon : redList) {
            if (count >= MAX_LINES) {
                break;
            }
            sb.append(formatLine(mon, true));
            count++;
        }

        return sb.toString();
    }

    /**
     * For each row, we show a link with "(+)" or "(-)" and the monster's name and
     * level.
     * No ID is displayed to the user, although we do pass npcId in the bypass.
     */
    private String formatLine(MonsterInstance mon, boolean isIgnored) {
        int npcId = mon.getNpcId();
        int lvl = mon.getLevel();
        String name = mon.getName();

        String color = (isIgnored ? "FF4444" : "66FF66");
        String sign = (isIgnored ? "(+)" : "(-)");

        return String.format(
                "<tr>"
                        + "<td width=30><a action=\"bypass _bbsautofarmfilter toggle %d\">%s</a></td>"
                        + "<td><font color=\"%s\">Lv. %d %s</font></td>"
                        + "</tr>\n",
                npcId, sign, color, lvl, name);
    }

    // ------------------------------------------------
    // ScriptFile stubs

    @Override
    public void onLoad() {
        // We want to register as a community board handler
        CommunityBoardManager.getInstance().registerHandler(this);

        // Also load existing ignore data from DB into memory
        CommunityTargetsDAO.getInstance().selectAll();
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
    public void onWriteCommand(Player player, String bypass,
            String arg1, String arg2, String arg3, String arg4, String arg5) {
        // no-op
    }
}
