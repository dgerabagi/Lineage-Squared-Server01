package l2ft.gameserver.autofarm;

/**
 * Holds configurable settings for auto-farm logic.
 * For this MVP, we'll keep them as static fields.
 * In a real scenario, you might read from a config file or DB.
 */
public final class AutoFarmConfig {
    // Whether autofarm is globally enabled
    public static boolean ENABLE_AUTO_FARM = true;

    // Interval between each auto-farm cycle in milliseconds
    public static long UPDATE_INTERVAL_MS = 1000L;

    // Range within which we look for monsters
    public static int SEARCH_RADIUS = 700;

    // [NEW] Toggle whether to use GeoEngine-based pathfinding instead of direct
    // movement
    public static boolean USE_PATHFINDING = true; // set to false if you want to disable

    // [DEBUG/LOGGING] Enables debug logs and yellow line drawing for path steps
    public static boolean DEBUG_PATHFINDING = true;

    private AutoFarmConfig() {
    }
}
