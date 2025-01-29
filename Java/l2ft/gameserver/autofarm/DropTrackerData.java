package l2ft.gameserver.autofarm;

import java.text.NumberFormat; // For commas in item counts
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;

import l2ft.gameserver.data.xml.holder.ItemHolder;
import l2ft.gameserver.templates.item.ItemTemplate;

/**
 * Tracks items that the user has looted while auto-farming.
 */
public final class DropTrackerData {
    private static final String KEY = "_dropTracker";

    // We track total drops in this map: itemId -> total count
    private final Map<Integer, Long> dropsMap = new HashMap<Integer, Long>();

    // We also track when the tracker started, so we can compute "items / sec"
    private long sessionStartTime;

    public static DropTrackerData get(AutoFarmState state) {
        Object obj = state.getCustomData(KEY);
        if (obj instanceof DropTrackerData) {
            return (DropTrackerData) obj;
        }
        DropTrackerData newData = new DropTrackerData();
        state.setCustomData(KEY, newData);
        return newData;
    }

    // Constructor: mark our session start time now
    public DropTrackerData() {
        sessionStartTime = System.currentTimeMillis();
    }

    // Add to the total count for a given itemId
    public synchronized void addDrop(int itemId, long count) {
        Long oldVal = dropsMap.get(Integer.valueOf(itemId));
        if (oldVal == null)
            oldVal = 0L;
        dropsMap.put(itemId, oldVal + count);
    }

    // Reset everything, including the start time
    public synchronized void reset() {
        dropsMap.clear();
        sessionStartTime = System.currentTimeMillis();
    }

    /**
     * Builds an HTML string with a table row per item.
     * Includes:
     * - Icon
     * - Name
     * - Formatted total count
     * - Items/second
     */
    public synchronized String buildHtmlRows() {
        if (dropsMap.isEmpty()) {
            return "<tr><td colspan='4'>No drops recorded yet.</td></tr>";
        }

        // We'll want a heading row:
        StringBuilder sb = new StringBuilder();
        sb.append("<tr>")
                .append("<td><b>Icon</b></td>")
                .append("<td><b>Item Name</b></td>")
                .append("<td align='right'><b>Total</b></td>")
                .append("<td align='right'><b>Per Sec</b></td>")
                .append("</tr>\n");

        // Compute how many seconds since we last reset
        long now = System.currentTimeMillis();
        long elapsedMillis = now - sessionStartTime;
        if (elapsedMillis < 1)
            elapsedMillis = 1; // Avoid division by zero
        double elapsedSec = elapsedMillis / 1000.0;

        // For thousands separators (e.g. "1,234"), we can use NumberFormat.getInstance:
        NumberFormat nf = NumberFormat.getInstance(Locale.US);

        for (Entry<Integer, Long> e : dropsMap.entrySet()) {
            int itemId = e.getKey().intValue();
            long count = e.getValue().longValue();

            // Attempt to get item template
            ItemTemplate template = ItemHolder.getInstance().getTemplate(itemId);
            String itemName;
            String iconName;

            if (template != null) {
                itemName = template.getName();
                iconName = template.getIcon();
                if (iconName == null || iconName.isEmpty()) {
                    iconName = "icon.etc_question_mark_i00";
                } else if (!iconName.startsWith("icon.")) {
                    // prepend 'icon.' if not found
                    iconName = "icon." + iconName;
                }
            } else {
                // Fallback if missing
                itemName = "Item#" + itemId;
                iconName = "icon.etc_question_mark_i00";
            }

            // Calculate items / second:
            double perSec = count / elapsedSec;

            // Format the total count with commas:
            String formattedCount = nf.format(count);
            // Format per-second as something like "123.4"
            // We'll keep only 1 decimal place, for example
            String formattedRate = String.format("%.1f", perSec);

            // Now build the row
            sb.append("<tr>");

            // Icon cell
            sb.append("<td width=40 align=center>")
                    .append("<img src=\"").append(iconName).append("\" width=\"32\" height=\"32\">")
                    .append("</td>");

            // Item name cell
            sb.append("<td width=300>")
                    .append("<font color=\"LEVEL\">").append(itemName).append("</font>")
                    .append("</td>");

            // Count cell (formatted with commas)
            sb.append("<td width=100 align=right>")
                    .append(formattedCount)
                    .append("</td>");

            // Items per second cell
            sb.append("<td width=100 align=right>")
                    .append(formattedRate)
                    .append("</td>");

            sb.append("</tr>\n");
        }

        return sb.toString();
    }
}
