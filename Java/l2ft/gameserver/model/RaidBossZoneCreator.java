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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dynamically creates two polygon-based "circular" zones around a spawned
 * RaidBoss:
 * - Outer zone radius: 2900
 * - Inner zone radius: 2500
 * 
 * Now also:
 * • Ignores bosses above level 76
 * • Z-limited to ±150 from boss's Z
 * 
 * NEW: The created zones are stored per boss so that when the boss dies
 * the de-level zones can be removed (after a delay) to avoid interference.
 */
public class RaidBossZoneCreator {
    private static final Logger _log = LoggerFactory.getLogger(RaidBossZoneCreator.class);

    private static final int OUTER_RADIUS = 2900;
    private static final int INNER_RADIUS = 2500;
    private static final int Z_RANGE_LIMIT = 200; // ±200

    // Store the pair of zones created for each bossId.
    private static final Map<Integer, ZonePair> _bossZones = new ConcurrentHashMap<Integer, ZonePair>();

    // Helper inner class to hold both outer and inner zones.
    private static class ZonePair {
        public final RaidBossOuterZone outerZone;
        public final RaidBossInnerZone innerZone;

        public ZonePair(RaidBossOuterZone outer, RaidBossInnerZone inner) {
            outerZone = outer;
            innerZone = inner;
        }
    }

    public static void createZonesForBoss(RaidBossInstance boss) {
        if (boss == null) {
            _log.warn("RaidBossZoneCreator: boss is null, skipping zone creation.");
            return;
        }

        // If boss's level is above 76, skip creating de-level zones.
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

        // Create the outer zone.
        RaidBossOuterZone outer = createOuterZone(bossId, loc, OUTER_RADIUS, reflect);
        outer.setActive(true);

        // Create the inner zone.
        RaidBossInnerZone inner = createInnerZone(bossId, loc, INNER_RADIUS, reflect);
        inner.setActive(true);

        // Store the zones so they can later be removed when the boss dies.
        _bossZones.put(bossId, new ZonePair(outer, inner));
    }

    /**
     * Removes the de–level zones for the given boss.
     * For each zone, every creature currently inside is forced to "leave"
     * (triggering onZoneLeave) and then the zone is deactivated.
     *
     * @param bossId the ID of the boss whose zones should be removed
     */
    public static void removeZonesForBoss(int bossId) {
        ZonePair pair = _bossZones.remove(bossId);
        if (pair == null) {
            _log.info("[RaidBossZoneCreator] No zones found for bossId=" + bossId + " to remove.");
            return;
        }
        _log.info("[RaidBossZoneCreator] Removing zones for bossId=" + bossId);

        removeZone(pair.outerZone);
        removeZone(pair.innerZone);
    }

    // Helper method to remove a zone: force all creatures to leave and then
    // deactivate.
    private static void removeZone(Zone zone) {
        if (zone == null) {
            return;
        }
        if (!zone.isActive()) {
            return;
        }
        // For every creature in the zone, simulate a zone leave.
        Creature[] creatures = zone.getObjects();
        for (int i = 0; i < creatures.length; i++) {
            zone.doLeave(creatures[i]);
        }
        // Deactivate the zone.
        zone.setActive(false);
        _log.debug("[RaidBossZoneCreator] Zone " + zone.getName() + " removed.");
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
     * Creates a polygon-based circle with 16 segments around the center of the
     * given radius,
     * with Z limited to ±150 from the boss's Z.
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
