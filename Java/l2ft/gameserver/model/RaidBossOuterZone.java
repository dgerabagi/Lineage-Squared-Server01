//
// C:\l2sq\Pac Project\Java\l2ft\gameserver\model\RaidBossOuterZone.java
//
package l2ft.gameserver.model;

import l2ft.gameserver.templates.ZoneTemplate;

/**
 * Outer zone object for a raidboss.
 * No forced-lower on crossing, but we beep & show popup.
 */
public class RaidBossOuterZone extends Zone {
    private int _bossId;

    public RaidBossOuterZone(ZoneTemplate template) {
        super(template.getType(), template);
        Object val = getTemplate().getParams().get("bossId");
        if (val != null) {
            try {
                _bossId = Integer.parseInt(val.toString());
            } catch (Exception e) {
            }
        }
    }

    public int getBossId() {
        return _bossId;
    }

    @Override
    protected void onZoneEnter(Creature actor) {
        super.onZoneEnter(actor);
        // actual logic in OnRaidBossZoneEvents
    }

    @Override
    protected void onZoneLeave(Creature actor) {
        super.onZoneLeave(actor);
    }
}
