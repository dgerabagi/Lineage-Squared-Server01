//
// C:\l2sq\Pac Project\Java\l2ft\gameserver\model\OnRaidBossZoneEvents.java
//
package l2ft.gameserver.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import l2ft.gameserver.listener.zone.OnZoneEnterLeaveListener;
import l2ft.gameserver.network.l2.s2c.PlaySound;
import l2ft.gameserver.network.l2.s2c.ExShowScreenMessage;
import l2ft.gameserver.network.l2.s2c.ExShowScreenMessage.ScreenMessageAlign;

/**
 * Zone event listener for "rbdelevel_OuterZone" and "rbdelevel_InnerZone".
 */
public class OnRaidBossZoneEvents implements OnZoneEnterLeaveListener {
    private static final OnRaidBossZoneEvents _instance = new OnRaidBossZoneEvents();
    private static final Logger _log = LoggerFactory.getLogger(OnRaidBossZoneEvents.class);

    public static OnRaidBossZoneEvents getInstance() {
        return _instance;
    }

    private OnRaidBossZoneEvents() {
    }

    @Override
    public void onZoneEnter(Zone zone, Creature actor) {
        if (!actor.isPlayer())
            return;
        Player player = actor.getPlayer();

        int bossId = 0;
        if (zone instanceof RaidBossOuterZone)
            bossId = ((RaidBossOuterZone) zone).getBossId();
        else if (zone instanceof RaidBossInnerZone)
            bossId = ((RaidBossInnerZone) zone).getBossId();

        if (bossId <= 0)
            return;

        int bossLevel = RaidBossDelevelManager.getInstance().getBossLevel(bossId);
        String bossName = RaidBossDelevelManager.getInstance().getBossName(bossId);

        _log.info("[OnRaidBossZoneEvents] onZoneEnter: zoneType=" + zone.getType()
                + ", player=" + player.getName()
                + ", bossId=" + bossId);

        if (zone instanceof RaidBossOuterZone) {
            // Show a beep & popup
            beepAndPopup(player, "Entering Level " + (bossLevel + 4) + " Raid Zone: " + bossName);

            // If forcibly-lowered, refresh pvp
            if (RaidBossDelevelManager.getInstance().isPlayerStillLowered(player)) {
                player.startPvPFlag(null);
            }
        } else if (zone instanceof RaidBossInnerZone) {
            // Inner zone text
            beepAndPopup(player, "Entered Level " + (bossLevel + 4) + " Raid Zone: " + bossName);

            // Possibly forcibly-lower if needed
            boolean changed = RaidBossDelevelManager.getInstance().applyDelevelIfNeeded(player, bossId);

            if (RaidBossDelevelManager.getInstance().isPlayerStillLowered(player)) {
                player.startPvPFlag(null);
            }

            if (changed) {
                // If forcibly-lowered just now, record originalExp
                RaidBossDelevelManager.getInstance().recordOriginalExp(player);
            }
        }
    }

    @Override
    public void onZoneLeave(Zone zone, Creature actor) {
        if (!actor.isPlayer())
            return;
        Player player = actor.getPlayer();

        int bossId = 0;
        if (zone instanceof RaidBossOuterZone)
            bossId = ((RaidBossOuterZone) zone).getBossId();
        else if (zone instanceof RaidBossInnerZone)
            bossId = ((RaidBossInnerZone) zone).getBossId();

        if (bossId <= 0)
            return;

        int bossLevel = RaidBossDelevelManager.getInstance().getBossLevel(bossId);
        String bossName = RaidBossDelevelManager.getInstance().getBossName(bossId);

        _log.info("[OnRaidBossZoneEvents] onZoneLeave: zoneType=" + zone.getType()
                + ", player=" + player.getName()
                + ", bossId=" + bossId);

        if (zone instanceof RaidBossInnerZone) {
            // No re-level or awarding here
            beepAndPopup(player, "Leaving Level " + (bossLevel + 4) + " Zone: " + bossName);

            if (RaidBossDelevelManager.getInstance().isPlayerStillLowered(player)) {
                player.startPvPFlag(null);
            }
        } else if (zone instanceof RaidBossOuterZone) {
            // The real re-level + awarding done upon leaving the OUTER zone
            beepAndPopup(player, "Leaving Level " + (bossLevel + 4) + " Zone: " + bossName);

            if (RaidBossDelevelManager.getInstance().isPlayerStillLowered(player)) {
                // start pvp
                player.startPvPFlag(null);

                // 1) Re-level
                RaidBossDelevelManager.getInstance().restoreOriginalLevel(player);

                // 2) Award XP/SP
                RaidBossDelevelManager.getInstance().distributeGainedXp(player);

                // 3) Remove forcibly-lowered from set
                RaidBossDelevelManager.getInstance().removePlayerFromLoweredSet(player);

                // Force update client
                player.sendChanges();
                player.sendUserInfo();
            } else {
                // Even if not forcibly-lowered anymore,
                // we might still have "SkipRaidExp" leftover if they never got XP or
                // parted mid-fight. Let's remove it:
                if ("1".equals(player.getVar("SkipRaidExp"))) {
                    // Remove the leftover skip and any other forcibly-lowered var
                    player.unsetVar("SkipRaidExp");
                    player.unsetVar("IgnoreDelevelStore");

                    // Also remove from the manager if still tracked:
                    if (RaidBossDelevelManager.getInstance().isPlayerStillLowered(player)) {
                        RaidBossDelevelManager.getInstance().removePlayerFromLoweredSet(player);
                    }
                }
            }
        }
    }

    private void beepAndPopup(Player pl, String text) {
        pl.sendPacket(new PlaySound("ItemSound.quest_middle"));
        pl.sendPacket(new ExShowScreenMessage(text, 3000, ScreenMessageAlign.TOP_CENTER, true));
    }
}
