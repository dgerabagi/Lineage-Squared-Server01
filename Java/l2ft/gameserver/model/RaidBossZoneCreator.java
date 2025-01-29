//
// C:\l2sq\Pac Project\Java\l2ft\gameserver\model\RaidBossZoneCreator.java
//
package l2ft.gameserver.model;

import l2ft.commons.collections.MultiValueSet;
import l2ft.gameserver.templates.StatsSet;
import l2ft.gameserver.templates.ZoneTemplate;
import l2ft.gameserver.model.instances.RaidBossInstance;
import l2ft.gameserver.utils.Location;
import l2ft.gameserver.model.entity.Reflection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import l2ft.commons.geometry.Polygon;

/**
 * Dynamically creates two polygon-based "circular" zones around a spawned
 * RaidBoss:
 * - Outer zone radius: 2900
 * - Inner zone radius: 2500
 * 
 * Now also:
 * • Ignores bosses above level 76
 * • Z-limited to ±150 from boss's Z
 */
public class RaidBossZoneCreator {
    private static final Logger _log = LoggerFactory.getLogger(RaidBossZoneCreator.class);

    private static final int OUTER_RADIUS = 2900;
    private static final int INNER_RADIUS = 2500;
    private static final int Z_RANGE_LIMIT = 150; // ±150

    public static void createZonesForBoss(RaidBossInstance boss) {
        if (boss == null) {
            _log.warn("RaidBossZoneCreator: boss is null, skipping zone creation.");
            return;
        }

        // If boss's level is above 76, skip
        int bossLevel = boss.getLevel();
        if (bossLevel > 76) {
            _log.info("[RaidBossZoneCreator] Boss " + boss.getName()
                    + " (lvl=" + bossLevel + ") >76 => skip zone creation.");
            return;
        }

        Location loc = boss.getLoc();
        int bossId = boss.getNpcId();
        Reflection reflect = boss.getReflection();

        _log.info("[RaidBossZoneCreator] Creating outer/inner zones for bossId=" + bossId
                + " (lvl=" + bossLevel
                + ") at loc=(" + loc.x + "," + loc.y + "," + loc.z
                + "), reflId=" + reflect.getId());

        // Outer ring
        RaidBossOuterZone outer = createOuterZone(bossId, loc, OUTER_RADIUS, reflect);
        outer.setActive(true);

        // Inner ring
        RaidBossInnerZone inner = createInnerZone(bossId, loc, INNER_RADIUS, reflect);
        inner.setActive(true);
    }

    private static RaidBossOuterZone createOuterZone(int bossId, Location center,
            int radius, Reflection reflect) {

        MultiValueSet<String> paramSet = new MultiValueSet<String>();
        paramSet.put("name", "BossOuterZone_" + bossId);
        paramSet.put("type", "rbdelevel_OuterZone");
        paramSet.put("bossId", String.valueOf(bossId));

        Territory terr = createCircularTerritory(center, radius);
        paramSet.put("territory", terr);

        StatsSet zoneStats = new StatsSet();
        zoneStats.putAll(paramSet);

        ZoneTemplate zTemplate = new ZoneTemplate(zoneStats);
        RaidBossOuterZone zone = new RaidBossOuterZone(zTemplate);

        zone.setReflection(reflect);
        zone.addListener(OnRaidBossZoneEvents.getInstance());
        _log.debug("[RaidBossZoneCreator] Outer zone created for bossId="
                + bossId + " radius=" + radius);
        return zone;
    }

    private static RaidBossInnerZone createInnerZone(int bossId, Location center,
            int radius, Reflection reflect) {

        MultiValueSet<String> paramSet = new MultiValueSet<String>();
        paramSet.put("name", "BossInnerZone_" + bossId);
        paramSet.put("type", "rbdelevel_InnerZone");
        paramSet.put("bossId", String.valueOf(bossId));

        Territory terr = createCircularTerritory(center, radius);
        paramSet.put("territory", terr);

        StatsSet zoneStats = new StatsSet();
        zoneStats.putAll(paramSet);

        ZoneTemplate zTemplate = new ZoneTemplate(zoneStats);
        RaidBossInnerZone zone = new RaidBossInnerZone(zTemplate);

        zone.setReflection(reflect);
        zone.addListener(OnRaidBossZoneEvents.getInstance());
        _log.debug("[RaidBossZoneCreator] Inner zone created for bossId="
                + bossId + " radius=" + radius);
        return zone;
    }

    /**
     * Creates a polygon-based circle with 16 segments around `center` of `radius`,
     * with Z limited to ±150 from boss Z.
     */
    private static Territory createCircularTerritory(Location center, int radius) {
        Polygon poly = new Polygon();
        int zMin = center.z - Z_RANGE_LIMIT;
        int zMax = center.z + Z_RANGE_LIMIT;
        poly.setZmin(zMin);
        poly.setZmax(zMax);

        int points = 16;
        double angleStep = 2 * Math.PI / points;
        for (int i = 0; i < points; i++) {
            double angle = i * angleStep;
            int tx = center.x + (int) (radius * Math.cos(angle));
            int ty = center.y + (int) (radius * Math.sin(angle));
            poly.add(tx, ty);
        }

        Territory territory = new Territory();
        territory.add(poly);
        return territory;
    }
}
