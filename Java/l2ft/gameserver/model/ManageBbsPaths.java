package l2ft.gameserver.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import l2ft.gameserver.utils.Location;

/**
 * This is an in-memory store of players' path definitions,
 * loaded from DB by CommunityPathsDAO.
 */
public class ManageBbsPaths {
    private static final ManageBbsPaths _instance = new ManageBbsPaths();

    // Key: charId => submap of pathName => PathRecord
    private final Map<Integer, Map<String, PathRecord>> pathsMap = new HashMap<Integer, Map<String, PathRecord>>();

    public static ManageBbsPaths getInstance() {
        return _instance;
    }

    public synchronized void addPath(int charId, String pathName, List<Location> points, boolean designMode) {
        Map<String, PathRecord> sub = pathsMap.get(Integer.valueOf(charId));
        if (sub == null) {
            sub = new HashMap<String, PathRecord>();
            pathsMap.put(Integer.valueOf(charId), sub);
        }
        PathRecord rec = new PathRecord();
        rec.charId = charId;
        rec.pathName = pathName;
        rec.points = (points == null ? new ArrayList<Location>() : points);
        rec.designMode = designMode;
        sub.put(pathName, rec);
    }

    public synchronized void removePath(int charId, String pathName) {
        Map<String, PathRecord> sub = pathsMap.get(Integer.valueOf(charId));
        if (sub != null)
            sub.remove(pathName);
    }

    public synchronized PathRecord getPath(int charId, String pathName) {
        Map<String, PathRecord> sub = pathsMap.get(Integer.valueOf(charId));
        if (sub == null)
            return null;
        return sub.get(pathName);
    }

    public synchronized List<PathRecord> getAllPaths(int charId) {
        List<PathRecord> list = new ArrayList<PathRecord>();
        Map<String, PathRecord> sub = pathsMap.get(Integer.valueOf(charId));
        if (sub == null)
            return list;
        list.addAll(sub.values());
        return list;
    }

    // For referencing externally
    public static class PathRecord {
        public int charId;
        public String pathName;
        public List<Location> points;
        public boolean designMode;
    }
}
