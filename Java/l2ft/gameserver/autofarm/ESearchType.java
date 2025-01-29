package l2ft.gameserver.autofarm;

/**
 * Represents the possible search types for auto-farm:
 * OFF = no searching
 * ASSIST = assist party-member's target
 * CLOSE = search ~400 range
 * NEAR = search ~1200 range
 * FAR = search ~2000 range
 */
public enum ESearchType {
    OFF("Off", 0),
    ASSIST("Assist", 0),
    CLOSE("Close", 400),
    NEAR("Near", 1200),
    FAR("Far", 2000);

    private final String displayName;
    private final int range;

    ESearchType(String displayName, int range) {
        this.displayName = displayName;
        this.range = range;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getRange() {
        return range;
    }
}
