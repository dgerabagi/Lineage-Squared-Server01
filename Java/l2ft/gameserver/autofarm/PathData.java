package l2ft.gameserver.autofarm;

import java.awt.Color;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.ArrayList;

import l2ft.gameserver.autofarm.PathData.PathInfo;
import l2ft.gameserver.model.Player;
import l2ft.gameserver.network.l2.s2c.ExServerPrimitive;
import l2ft.gameserver.utils.Location;

public class PathData {
    public static final String KEY = "_pathData";

    private final Map<String, PathInfo> _paths = new HashMap<String, PathInfo>();
    private String selectedPathName = null;

    public static PathData get(AutoFarmState state) {
        Object obj = state.getCustomData(KEY);
        if (obj instanceof PathData) {
            return (PathData) obj;
        }
        PathData data = new PathData();
        state.setCustomData(KEY, data);
        return data;
    }

    public static class PathInfo {
        public List<Location> points = new ArrayList<Location>();
        public boolean designMode = false;
    }

    public boolean hasPath(String name) {
        return _paths.containsKey(name);
    }

    public void createPath(String name) {
        PathInfo pi = new PathInfo();
        _paths.put(name, pi);
    }

    public PathInfo getPath(String name) {
        return _paths.get(name);
    }

    public void removePath(String name) {
        _paths.remove(name);
        if (name != null && name.equals(selectedPathName)) {
            selectedPathName = null;
        }
    }

    public void addPoint(String pathName, int x, int y, int z) {
        PathInfo pi = _paths.get(pathName);
        if (pi != null) {
            pi.points.add(new Location(x, y, z));
        }
    }

    public boolean toggleDesignMode(String pathName) {
        PathInfo pi = _paths.get(pathName);
        if (pi == null)
            return false;
        pi.designMode = !pi.designMode;
        return pi.designMode;
    }

    public boolean isDesignMode(String pathName) {
        PathInfo pi = _paths.get(pathName);
        return (pi != null && pi.designMode);
    }

    public void setSelectedPath(String pathName) {
        if (_paths.containsKey(pathName)) {
            selectedPathName = pathName;
        } else {
            selectedPathName = null;
        }
    }

    public String getSelectedPath() {
        return selectedPathName;
    }

    /**
     * Renders lines for:
     * - any path in designMode => color = CYAN
     * - the single selected path => green if (autofarm running + movement=PATH),
     * else red
     * - skip all other paths
     */
    public void renderAllPaths(Player player, boolean ignoredBool) {
        if (player == null) {
            return;
        }

        // 1) Check if movement is OFF. If so, just clear everything.
        AutoFarmState st = player.getAutoFarmState();
        if (st == null || st.getMoveMethod() == AutoFarmState.EMoveMethod.OFF) {
            clearPrimitive(player, "AllPaths");
            return;
        }

        if (_paths.isEmpty()) {
            clearPrimitive(player, "AllPaths");
            return;
        }

        // Build a new ExServerPrimitive for all path lines
        ExServerPrimitive sp = new ExServerPrimitive("AllPaths", player.getX(), player.getY(), -65535);

        boolean isRunning = st.isActive();
        boolean isPathMode = (st.getMoveMethod() == AutoFarmState.EMoveMethod.PATH);

        for (Map.Entry<String, PathInfo> e : _paths.entrySet()) {
            String pathName = e.getKey();
            PathInfo pi = e.getValue();

            // We need at least 2 points to draw lines
            if (pi.points.size() < 2) {
                continue;
            }

            Color colorToUse;
            if (pi.designMode) {
                colorToUse = Color.CYAN;
            } else if (pathName.equals(selectedPathName)) {
                // green if running + PATH mode, else red
                if (isRunning && isPathMode) {
                    colorToUse = Color.GREEN;
                } else {
                    colorToUse = Color.RED;
                }
            } else {
                // skip unselected, non-design paths
                continue;
            }

            // Render line segments between consecutive points
            for (int i = 0; i < pi.points.size() - 1; i++) {
                Location A = pi.points.get(i);
                Location B = pi.points.get(i + 1);
                sp.addLine(
                        pathName + " - " + i,
                        colorToUse,
                        false,
                        A.x, A.y, A.z,
                        B.x, B.y, B.z);
            }
        }

        // Finally send the lines to the player's client
        player.sendPacket(sp);
    }

    private void clearPrimitive(Player player, String name) {
        if (player == null)
            return;

        // Create an empty ExServerPrimitive to overwrite any existing lines named
        // 'name'
        ExServerPrimitive blank = new ExServerPrimitive(name, player.getX(), player.getY(), -65535);
        player.sendPacket(blank);
    }

    public Set<String> getAllPathNames() {
        return _paths.keySet();
    }

    /**
     * Build the path listing for the Path Editor main page:
     * - The "Name" column occupies two thirds width
     * - The three buttons (Edit, Delete, Select) occupies one third width
     */
    public String buildHtmlList() {
        if (_paths.isEmpty()) {
            return "<tr><td>No Paths defined yet.</td></tr>";
        }

        StringBuilder sb = new StringBuilder();
        for (String name : _paths.keySet()) {
            sb.append("<tr>");

            // 1) Path Name Column
            sb.append("<td width=\"402\">");
            sb.append("<font color=\"LEVEL\">").append(name).append("</font>");
            if (name.equals(selectedPathName)) {
                sb.append(" <font color=\"00FF00\">(Selected)</font>");
            }
            sb.append("</td>");

            // 2) Edit Button Column
            sb.append("<td width=\"66\" align=\"center\">");
            sb.append("<button value=\"Edit\" action=\"bypass _bbsautofarmpath edit ")
                    .append(name)
                    .append("\" width=\"60\" height=\"25\" ")
                    .append("back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\">");

            // 3) Delete Button Column
            sb.append("</td><td width=\"66\" align=\"center\">");
            sb.append("<button value=\"Delete\" action=\"bypass _bbsautofarmpath remove ")
                    .append(name)
                    .append("\" width=\"60\" height=\"25\" ")
                    .append("back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\">");

            // 4) Select Button Column
            sb.append("</td><td width=\"66\" align=\"center\">");
            sb.append("<button value=\"Select\" action=\"bypass _bbsautofarmpath select ")
                    .append(name)
                    .append("\" width=\"60\" height=\"25\" ")
                    .append("back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\">");

            sb.append("</td></tr>\n");
        }

        return sb.toString();
    }
}
