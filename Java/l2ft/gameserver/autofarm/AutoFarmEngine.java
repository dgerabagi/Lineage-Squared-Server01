package l2ft.gameserver.autofarm;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import l2ft.gameserver.ThreadPoolManager;
import l2ft.gameserver.dao.CommunityPathsDAO;
import l2ft.gameserver.model.ManageBbsPaths;
import l2ft.gameserver.model.ManageBbsPaths.PathRecord;
import l2ft.gameserver.model.Player;
import l2ft.gameserver.utils.Location;

public final class AutoFarmEngine {
    private static final AutoFarmEngine _instance = new AutoFarmEngine();
    private final Map<Integer, AutoFarmState> _activeFarms = new ConcurrentHashMap<Integer, AutoFarmState>();

    private AutoFarmEngine() {
    }

    public static AutoFarmEngine getInstance() {
        return _instance;
    }

    /**
     * Retrieve or create the player's AutoFarmState (plus PathData).
     */
    public AutoFarmState getOrCreateState(Player player) {
        if (player == null)
            return null;

        AutoFarmState st = _activeFarms.get(player.getObjectId());
        if (st == null) {
            st = new AutoFarmState(player);
            _activeFarms.put(player.getObjectId(), st);
            player.setAutoFarmState(st);

            // Load ephemeral PathData from DB
            syncPathsFromDB(player, st);
        }
        return st;
    }

    private void syncPathsFromDB(Player player, AutoFarmState state) {
        if (player == null || state == null)
            return;
        PathData pd = PathData.get(state);

        int charId = player.getObjectId();
        // load from in-memory ManageBbsPaths
        for (PathRecord rec : ManageBbsPaths.getInstance().getAllPaths(charId)) {
            pd.createPath(rec.pathName);
            if (rec.designMode) {
                // forcibly set designMode
                pd.toggleDesignMode(rec.pathName);
            }
            if (rec.points != null) {
                for (Location loc : rec.points) {
                    pd.addPoint(rec.pathName, loc.x, loc.y, loc.z);
                }
            }
        }
    }

    public AutoFarmState getAutoFarmState(int objectId) {
        return _activeFarms.get(objectId);
    }

    // Check if player is actively farming
    public boolean isAutoFarming(Player player) {
        if (player == null)
            return false;
        AutoFarmState st = getAutoFarmState(player.getObjectId());
        return (st != null && st.isActive());
    }

    // Called from your bbs commands, etc.
    public void startAutoFarm(Player player) {
        if (player == null)
            return;
        AutoFarmState st = getOrCreateState(player);
        if (!st.isActive()) {
            st.setActive(true);
            scheduleTask(st);
            player.sendMessage("AutoFarm started.");

            // re-render path lines in green if a path is selected
            PathData pd = PathData.get(st);
            if (pd != null) {
                pd.renderAllPaths(player, true);
            }

        } else {
            player.sendMessage("AutoFarm is already active.");
        }
    }

    public void stopAutoFarm(Player player) {
        if (player == null)
            return;
        AutoFarmState st = getAutoFarmState(player.getObjectId());
        if (st != null && st.isActive()) {
            st.setActive(false);
            player.sendMessage("AutoFarm stopped.");

            // re-render path lines in red if a path is selected
            PathData pd = PathData.get(st);
            if (pd != null) {
                pd.renderAllPaths(player, false);
            }

        } else {
            player.sendMessage("AutoFarm is not running.");
        }
    }

    private void scheduleTask(AutoFarmState farmState) {
        farmState.cancelTask();
        long delayMs = AutoFarmConfig.UPDATE_INTERVAL_MS;
        farmState.setTask(ThreadPoolManager.getInstance().scheduleAtFixedRate(
                new AutoFarmTask(farmState), 0L, delayMs));
    }

    public void scheduleOrResumeTask(Player player) {
        if (player == null)
            return;
        AutoFarmState st = getOrCreateState(player);
        if (st.getTask() != null && !st.getTask().isCancelled()) {
            // already scheduled
            return;
        }
        scheduleTask(st);
    }

    public void removeState(int objectId) {
        AutoFarmState st = _activeFarms.remove(objectId);
        if (st != null) {
            st.cancelTask();
            st.setActive(false);
        }
    }

    public void shutdownAll() {
        for (AutoFarmState st : _activeFarms.values()) {
            st.setActive(false);
            st.cancelTask();
        }
        _activeFarms.clear();
    }
}
