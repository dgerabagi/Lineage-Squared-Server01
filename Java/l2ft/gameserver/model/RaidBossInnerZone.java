//
// C:\l2sq\Pac Project\Java\l2ft\gameserver\model\RaidBossInnerZone.java
//
package l2ft.gameserver.model;

import l2ft.gameserver.templates.ZoneTemplate;

/**
 * Inner zone object for a raidboss.
 * If crossing from outer to inner => forcibly-lower if needed.
 */
public class RaidBossInnerZone extends Zone {
    private int _bossId;

    public RaidBossInnerZone(ZoneTemplate template) {
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
    }

    @Override
    protected void onZoneLeave(Creature actor) {
        super.onZoneLeave(actor);
    }
}
