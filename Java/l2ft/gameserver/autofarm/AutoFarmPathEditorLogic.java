package l2ft.gameserver.autofarm;

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import l2ft.gameserver.Config;
import l2ft.gameserver.ThreadPoolManager;
import l2ft.gameserver.dao.CommunityPathsDAO;
import l2ft.gameserver.data.htm.HtmCache;
import l2ft.gameserver.model.ManageBbsPaths;
import l2ft.gameserver.model.ManageBbsPaths.PathRecord;
import l2ft.gameserver.model.Player;
import l2ft.gameserver.network.l2.s2c.ShowBoard;
import l2ft.gameserver.scripts.Functions;
import l2ft.gameserver.utils.BbsUtil;
import l2ft.gameserver.utils.Location;

/**
 * Core logic for AutoFarm Path Editor.
 * Also includes "Sim" logic that moves the player
 * along each point in an infinite loop until turned off.
 *
 * Now updated to allow path names with spaces.
 */
public final class AutoFarmPathEditorLogic {
    private static final Logger _log = LoggerFactory.getLogger(AutoFarmPathEditorLogic.class);

    private static final AutoFarmPathEditorLogic _instance = new AutoFarmPathEditorLogic();

    // HTML file names
    private static final String PATH_HTML = "autofarm/bbs_autoFarmPath.htm";
    private static final String DESIGN_HTML = "autofarm/bbs_autoFarmPathDesign.htm";

    // We track each player's simulation via a PathSimTask
    // Key = player's objectId
    private final Map<Integer, ScheduledFuture<?>> _simTasks = new ConcurrentHashMap<Integer, ScheduledFuture<?>>();

    private AutoFarmPathEditorLogic() {
    }

    public static AutoFarmPathEditorLogic getInstance() {
        return _instance;
    }

    /**
     * The "entry point" for space-based commands:
     * _bbsautofarmpath open
     * _bbsautofarmpath remove Some Path Name
     * _bbsautofarmpath edit Another Path
     * _bbsautofarmpath select My Path
     * _bbsautofarmpath sim My Path
     * _bbsautofarmpath back Some Other Path
     * etc.
     *
     * We now allow spaces by reading the entire remainder of the line after the
     * command,
     * except for certain commands like addpoint (which require x y z at the end).
     */
    public void handleSpaceBasedBypass(Player player, String cmd, StringTokenizer st) {
        if (player == null)
            return;
        AutoFarmState state = AutoFarmEngine.getInstance().getOrCreateState(player);
        if (state == null)
            return;

        PathData pd = PathData.get(state);

        if ("open".equals(cmd)) {
            // Show the main path editor page
            showPathPage(player, pd);
        } else if ("remove".equals(cmd)) {
            // e.g. _bbsautofarmpath remove Some Path With Spaces
            String pathName = getRemainder(st);
            if (pathName.isEmpty())
                pathName = "Untitled";

            if (pd.hasPath(pathName)) {
                pd.removePath(pathName);
                // remove from DB + in-memory
                ManageBbsPaths.getInstance().removePath(player.getObjectId(), pathName);
                CommunityPathsDAO.getInstance().delete(player.getObjectId(), pathName);

                player.sendMessage("Removed path: " + pathName);
            } else {
                player.sendMessage("No such path: " + pathName);
            }
            showPathPage(player, pd);
        } else if ("edit".equals(cmd)) {
            // e.g. _bbsautofarmpath edit My Path
            String pathName = getRemainder(st);
            if (pathName.isEmpty() || !pd.hasPath(pathName)) {
                player.sendMessage("No such path.");
                showPathPage(player, pd);
                return;
            }
            showDesignPage(player, pd, pathName);
        } else if ("select".equals(cmd)) {
            // e.g. _bbsautofarmpath select My Path
            String pathName = getRemainder(st);
            if (pathName.isEmpty() || !pd.hasPath(pathName)) {
                player.sendMessage("No such path to select.");
                showPathPage(player, pd);
                return;
            }

            pd.setSelectedPath(pathName);
            player.sendMessage("Selected path: " + pathName);

            // Re-render lines so only that path is shown (plus any design-mode paths)
            boolean isRunning = AutoFarmEngine.getInstance().isAutoFarming(player);
            pd.renderAllPaths(player, isRunning);

            showPathPage(player, pd);
        } else if ("togglemode".equals(cmd)) {
            // e.g. _bbsautofarmpath toggleMode My Path
            String pathName = getRemainder(st);
            if (pathName.isEmpty() || !pd.hasPath(pathName)) {
                player.sendMessage("No such path.");
                showPathPage(player, pd);
                return;
            }
            boolean newMode = pd.toggleDesignMode(pathName);

            // update DB
            PathRecord rec = ManageBbsPaths.getInstance().getPath(player.getObjectId(), pathName);
            if (rec != null) {
                rec.designMode = newMode;
                CommunityPathsDAO.getInstance().update(rec.charId, rec.pathName, rec.points, rec.designMode);
            }

            player.sendMessage("Design mode " + (newMode ? "ENABLED" : "DISABLED") + " for: " + pathName);

            // re-render lines
            boolean isRunning = AutoFarmEngine.getInstance().isAutoFarming(player);
            pd.renderAllPaths(player, isRunning);

            showDesignPage(player, pd, pathName);
        } else if ("addpoint".equals(cmd)) {
            // e.g. _bbsautofarmpath addpoint My Path 12345 67890 -345
            // We know the last 3 tokens are x,y,z, so parse them off the end
            int totalTokens = st.countTokens();
            if (totalTokens < 4) {
                player.sendMessage("Usage: addpoint <pathName> <x> <y> <z>");
                return;
            }
            // We'll parse from the end: x,y,z
            // Everything else forms the pathName
            String[] tokensArray = new String[totalTokens];
            for (int i = 0; i < totalTokens; i++)
                tokensArray[i] = st.nextToken();

            // The last 3 are coords
            String xStr = tokensArray[totalTokens - 3];
            String yStr = tokensArray[totalTokens - 2];
            String zStr = tokensArray[totalTokens - 1];

            // The pathName is everything before that
            StringBuilder sbPath = new StringBuilder();
            for (int i = 0; i < (totalTokens - 3); i++) {
                sbPath.append(tokensArray[i]);
                if (i < (totalTokens - 4))
                    sbPath.append(" ");
            }
            String pathName = sbPath.toString();
            handleAddPoint(player, pd, pathName, xStr, yStr, zStr);
        } else if ("removepoint".equals(cmd)) {
            // e.g. _bbsautofarmpath removepoint My Path 0
            // The last token is the index, the rest is the pathName
            int totalTokens = st.countTokens();
            if (totalTokens < 2) {
                player.sendMessage("Usage: removepoint <pathName> <index>");
                return;
            }
            String[] tokensArray = new String[totalTokens];
            for (int i = 0; i < totalTokens; i++)
                tokensArray[i] = st.nextToken();

            String idxStr = tokensArray[totalTokens - 1];
            StringBuilder sbPath = new StringBuilder();
            for (int i = 0; i < (totalTokens - 1); i++) {
                sbPath.append(tokensArray[i]);
                if (i < (totalTokens - 2))
                    sbPath.append(" ");
            }
            String pathName = sbPath.toString();
            handleRemovePoint(player, pd, pathName, idxStr);
        } else if ("sim".equals(cmd)) {
            // e.g. _bbsautofarmpath sim My Path
            String pathName = getRemainder(st);
            if (pathName.isEmpty())
                pathName = "Untitled";
            handleSimCommand(player, pd, pathName);
        } else if ("back".equals(cmd)) {
            // e.g. _bbsautofarmpath back My Path
            String pathName = getRemainder(st);
            if (!pathName.isEmpty() && pd.isDesignMode(pathName)) {
                // forcibly disable design mode
                pd.toggleDesignMode(pathName);
                player.sendMessage("Design mode DISABLED for: " + pathName);
                // update DB
                PathRecord rec = ManageBbsPaths.getInstance().getPath(player.getObjectId(), pathName);
                if (rec != null && rec.designMode) {
                    rec.designMode = false;
                    CommunityPathsDAO.getInstance().update(rec.charId, rec.pathName, rec.points, rec.designMode);
                }
            }
            showPathPage(player, pd);
        } else {
            // fallback
            showPathPage(player, pd);
        }
    }

    /**
     * Called from the "colon-based" create approach:
     * e.g. bypass _bbsautofarmpath:create:open: $PNAME
     */
    public void handleCreatePath(Player player, String typedName) {
        if (player == null)
            return;

        AutoFarmState state = AutoFarmEngine.getInstance().getOrCreateState(player);
        if (state == null)
            return;

        PathData pd = PathData.get(state);
        if (typedName.isEmpty()) {
            typedName = "Untitled";
        }

        if (pd.hasPath(typedName)) {
            player.sendMessage("A path named '" + typedName + "' already exists.");
        } else {
            // ephemeral
            pd.createPath(typedName);

            // DB
            CommunityPathsDAO.getInstance().insert(player.getObjectId(),
                    typedName,
                    new ArrayList<Location>(),
                    false);

            // in-memory
            ManageBbsPaths.getInstance().addPath(player.getObjectId(),
                    typedName,
                    new ArrayList<Location>(),
                    false);

            player.sendMessage("Created new path: " + typedName);
        }

        showPathPage(player, pd);
    }

    private void handleAddPoint(Player player, PathData pd, String pathName,
            String xStr, String yStr, String zStr) {
        if (!pd.hasPath(pathName)) {
            player.sendMessage("No such path: " + pathName);
            return;
        }
        try {
            int x = Integer.parseInt(xStr);
            int y = Integer.parseInt(yStr);
            int z = Integer.parseInt(zStr);
            pd.addPoint(pathName, x, y, z);

            // Re-render lines
            boolean running = AutoFarmEngine.getInstance().isAutoFarming(player);
            pd.renderAllPaths(player, running);

            // Also update DB
            PathRecord rec = ManageBbsPaths.getInstance().getPath(player.getObjectId(), pathName);
            if (rec != null) {
                rec.points.add(new Location(x, y, z));
                CommunityPathsDAO.getInstance().update(rec.charId, rec.pathName, rec.points, rec.designMode);
            }

            player.sendMessage("Point added to path: " + pathName + " (" + x + "," + y + "," + z + ")");
        } catch (NumberFormatException ex) {
            player.sendMessage("Invalid coords for addpoint.");
        }
        showDesignPage(player, pd, pathName);
    }

    private void handleRemovePoint(Player player, PathData pd, String pathName, String idxStr) {
        if (!pd.hasPath(pathName)) {
            player.sendMessage("No such path: " + pathName);
            return;
        }
        try {
            int idx = Integer.parseInt(idxStr);
            PathData.PathInfo pi = pd.getPath(pathName);
            if (pi != null && idx >= 0 && idx < pi.points.size()) {
                pi.points.remove(idx);

                // Also update DB
                PathRecord rec = ManageBbsPaths.getInstance().getPath(player.getObjectId(), pathName);
                if (rec != null && idx < rec.points.size()) {
                    rec.points.remove(idx);
                    CommunityPathsDAO.getInstance().update(rec.charId, rec.pathName, rec.points, rec.designMode);
                }

                // re-render
                boolean running = AutoFarmEngine.getInstance().isAutoFarming(player);
                pd.renderAllPaths(player, running);

                player.sendMessage("Removed point index " + idx);
            } else {
                player.sendMessage("Index out of range or invalid: " + idx);
            }
        } catch (NumberFormatException ex) {
            player.sendMessage("Invalid index for removepoint.");
        }
        showDesignPage(player, pd, pathName);
    }

    /**
     * Show the main path list page.
     */
    public void showPathPage(Player player, PathData pd) {
        if (player == null || pd == null)
            return;

        String html = HtmCache.getInstance().getNotNull(Config.BBS_HOME_DIR + PATH_HTML, player);
        String pathList = pd.buildHtmlList();
        html = html.replace("%PATH_LIST%", pathList);

        html = BbsUtil.htmlAll(html, player);
        ShowBoard.separateAndSend(html, player);
    }

    /**
     * Show the design page for a particular path.
     */
    public void showDesignPage(Player player, PathData pd, String pathName) {
        if (player == null || pd == null)
            return;

        PathData.PathInfo pi = pd.getPath(pathName);
        if (pi == null) {
            showPathPage(player, pd);
            return;
        }

        String html = HtmCache.getInstance().getNotNull(Config.BBS_HOME_DIR + DESIGN_HTML, player);

        html = html.replace("%PATH_NAME%", pathName);

        String dmStatus = (pi.designMode ? "<font color=\"00FF00\">On</font>" : "<font color=\"FF0000\">Off</font>");
        html = html.replace("%DESIGN_MODE_STATUS%", dmStatus);

        // Build the list of points
        StringBuilder sbPoints = new StringBuilder();
        if (pi.points.isEmpty()) {
            sbPoints.append("<tr><td>No points yet.</td></tr>");
        } else {
            for (int i = 0; i < pi.points.size(); i++) {
                Location loc = pi.points.get(i);
                char label = (char) ('A' + i);

                sbPoints.append("<tr>");
                sbPoints.append("<td>")
                        .append(label)
                        .append(" - X:")
                        .append(loc.x)
                        .append(", Y:")
                        .append(loc.y)
                        .append(", Z:")
                        .append(loc.z)
                        .append("</td>");

                // remove button
                sbPoints.append("<td><button value=\"\" action=\"bypass _bbsautofarmpath removepoint ")
                        .append(pathName).append(" ").append(i)
                        .append("\" width=\"32\" height=\"32\" ")
                        .append("back=\"L2UI_CT1.MiniMap_DF_PlusBtn_Red_Down\" ")
                        .append("fore=\"L2UI_CT1.MiniMap_DF_PlusBtn_Red\"")
                        .append("></td>");

                sbPoints.append("</tr>");
            }
        }
        html = html.replace("%POINTS_LIST%", sbPoints.toString());

        html = BbsUtil.htmlAll(html, player);
        ShowBoard.separateAndSend(html, player);
    }

    /**
     * Force show design page for SHIFT+click scenario. (Optional usage)
     */
    public void forceShowDesignPage(Player player, String pathName) {
        if (player == null)
            return;
        AutoFarmState st = AutoFarmEngine.getInstance().getOrCreateState(player);
        if (st == null)
            return;
        PathData pd = PathData.get(st);
        showDesignPage(player, pd, pathName);
    }

    // -------------------------------------------------------------------------
    // SIMULATION LOGIC

    /**
     * Handles the "sim" command from the user:
     * - If that player is not yet simming, we start it.
     * - If they are already simming, we stop it.
     */
    private void handleSimCommand(Player player, PathData pd, String pathName) {
        if (!pd.hasPath(pathName)) {
            player.sendMessage("No such path: " + pathName);
            return;
        }

        // Check if they're already simming
        if (_simTasks.containsKey(player.getObjectId())) {
            // STOP existing sim
            stopSimFor(player);
            player.sendMessage("Stopped path simulation for: " + pathName);
            showDesignPage(player, pd, pathName);
            return;
        }

        // Otherwise, START sim
        PathSimTask task = new PathSimTask(player.getObjectId(), pathName);
        // run more frequently for smoother movement
        ScheduledFuture<?> future = ThreadPoolManager.getInstance().scheduleAtFixedRate(task, 200L, 200L);
        _simTasks.put(player.getObjectId(), future);

        player.sendMessage("Started path simulation for: " + pathName + ". (Click Sim again to stop.)");
        showDesignPage(player, pd, pathName);
    }

    /** Force stop the sim for a given player. */
    private void stopSimFor(Player player) {
        ScheduledFuture<?> future = _simTasks.remove(player.getObjectId());
        if (future != null) {
            future.cancel(false);
        }
    }

    /**
     * Inner class: PathSimTask
     * Moves the player from point 0 -> 1 -> 2 -> ... -> end, then loops back to 0.
     */
    private static class PathSimTask implements Runnable {
        private final int _playerObjId;
        private final String _pathName;
        private int _index = 0; // current point

        PathSimTask(int playerObjId, String pathName) {
            _playerObjId = playerObjId;
            _pathName = pathName;
        }

        @Override
        public void run() {
            // Retrieve the player
            Player player = l2ft.gameserver.model.GameObjectsStorage.getPlayer(_playerObjId);
            if (player == null || player.isInOfflineMode() || player.isInStoreMode()) {
                // If offline or no longer in game, we can stop
                stopMe();
                return;
            }

            AutoFarmState st = player.getAutoFarmState();
            if (st == null) {
                stopMe();
                return;
            }

            PathData pd = PathData.get(st);
            PathData.PathInfo pi = pd.getPath(_pathName);
            if (pi == null || pi.points.isEmpty()) {
                player.sendMessage("Path is empty or missing, stopping sim.");
                stopMe();
                return;
            }

            // If index is out of range, loop back to 0 => infinite loop
            if (_index >= pi.points.size()) {
                _index = 0;
            }

            // Move toward the next point
            Location loc = pi.points.get(_index);
            double dist = player.getDistance(loc.x, loc.y, loc.z);

            // If close enough, increment index
            if (dist < 64.0) {
                _index++;
            } else {
                // Move
                player.moveToLocation(loc, 0, false);
            }
        }

        private void stopMe() {
            // forcibly cancel this task (from within)
            ThreadPoolManager.getInstance().schedule(new Runnable() {
                @Override
                public void run() {
                    Thread.currentThread().interrupt();
                }
            }, 0L);
        }
    }

    // -------------------------------------------------------------------------
    // Helper to read the entire remainder of tokens as one string (for path names
    // w/ spaces)
    private String getRemainder(StringTokenizer st) {
        if (!st.hasMoreTokens())
            return "";
        StringBuilder sb = new StringBuilder();
        while (st.hasMoreTokens()) {
            sb.append(st.nextToken());
            if (st.hasMoreTokens())
                sb.append(" ");
        }
        return sb.toString().trim();
    }
}
