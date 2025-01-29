package l2ft.gameserver.utils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import l2ft.gameserver.data.xml.holder.InstantZoneHolder;
import l2ft.gameserver.instancemanager.ReflectionManager;
import l2ft.gameserver.model.CommandChannel;
import l2ft.gameserver.model.Party;
import l2ft.gameserver.model.Player;
import l2ft.gameserver.model.Zone;
import l2ft.gameserver.model.entity.Reflection;
import l2ft.gameserver.model.instances.DoorInstance;
import l2ft.gameserver.templates.InstantZone;

public class ReflectionUtils {
	/**
	 * Returns door from the default reflection
	 */
	public static DoorInstance getDoor(int id) {
		return ReflectionManager.DEFAULT.getDoor(id);
	}

	/**
	 * Returns zone from the default reflection
	 */
	public static Zone getZone(String name) {
		return ReflectionManager.DEFAULT.getZone(name);
	}

	public static List<Zone> getZonesByType(Zone.ZoneType zoneType) {
		Collection<Zone> zones = ReflectionManager.DEFAULT.getZones();
		if (zones.isEmpty())
			return Collections.emptyList();

		List<Zone> result = new ArrayList<Zone>();
		for (Zone z : zones)
			if (z.getType() == zoneType)
				result.add(z);

		return result;
	}

	public static Reflection enterReflection(Player invoker, int instancedZoneId) {
		InstantZone iz = InstantZoneHolder.getInstance().getInstantZone(instancedZoneId);
		return enterReflection(invoker, new Reflection(), iz);
	}

	public static Reflection enterReflection(Player invoker, Reflection r, int instancedZoneId) {
		InstantZone iz = InstantZoneHolder.getInstance().getInstantZone(instancedZoneId);
		return enterReflection(invoker, r, iz);
	}

	public static Reflection enterReflection(Player invoker, Reflection r, InstantZone iz) {
		// Gracefully handle missing InstantZone
		if (iz == null) {
			System.err.println("[ReflectionUtils] Attempted to enter unknown instance zone (null).");
			if (invoker != null)
				invoker.sendMessage("That instance doesn't seem to exist. Contact admins.");
			return null;
		}

		// Initialize Reflection data from InstantZone
		r.init(iz);

		if (r.getReturnLoc() == null && invoker != null)
			r.setReturnLoc(invoker.getLoc());

		switch (iz.getEntryType()) {
			case SOLO:
				if (invoker != null) {
					if (iz.getRemovedItemId() > 0)
						ItemFunctions.removeItem(invoker, iz.getRemovedItemId(), iz.getRemovedItemCount(), true);
					if (iz.getGiveItemId() > 0)
						ItemFunctions.addItem(invoker, iz.getGiveItemId(), iz.getGiveItemCount(), true);
					if (iz.isDispelBuffs())
						invoker.dispelBuffs();
					if (iz.getSetReuseUponEntry()
							&& iz.getResetReuse().next(System.currentTimeMillis()) > System.currentTimeMillis()) {
						invoker.setInstanceReuse(iz.getId(), System.currentTimeMillis());
					}
					invoker.setVar("backCoords", invoker.getLoc().toXYZString(), -1);
					invoker.teleToLocation(iz.getTeleportCoord(), r);
				}
				break;

			case PARTY:
				if (invoker != null) {
					Party party = invoker.getParty();
					if (party != null)
						party.setReflection(r);
					r.setParty(party);

					for (Player member : party.getPartyMembers()) {
						if (iz.getRemovedItemId() > 0)
							ItemFunctions.removeItem(member, iz.getRemovedItemId(), iz.getRemovedItemCount(), true);
						if (iz.getGiveItemId() > 0)
							ItemFunctions.addItem(member, iz.getGiveItemId(), iz.getGiveItemCount(), true);
						if (iz.isDispelBuffs())
							member.dispelBuffs();
						if (iz.getSetReuseUponEntry())
							member.setInstanceReuse(iz.getId(), System.currentTimeMillis());
						member.setVar("backCoords", invoker.getLoc().toXYZString(), -1);
						member.teleToLocation(iz.getTeleportCoord(), r);
					}
				}
				break;

			case COMMAND_CHANNEL:
				if (invoker != null) {
					Party commparty = invoker.getParty();
					CommandChannel cc = commparty != null ? commparty.getCommandChannel() : null;

					if (cc == null) {
						// Fallback: treat it like a plain party
						if (commparty != null)
							commparty.setReflection(r);
						r.setParty(commparty);

						for (Player member : commparty.getPartyMembers()) {
							if (iz.getRemovedItemId() > 0)
								ItemFunctions.removeItem(member, iz.getRemovedItemId(), iz.getRemovedItemCount(), true);
							if (iz.getGiveItemId() > 0)
								ItemFunctions.addItem(member, iz.getGiveItemId(), iz.getGiveItemCount(), true);
							if (iz.isDispelBuffs())
								member.dispelBuffs();
							if (iz.getSetReuseUponEntry())
								member.setInstanceReuse(iz.getId(), System.currentTimeMillis());
							member.setVar("backCoords", invoker.getLoc().toXYZString(), -1);
							member.teleToLocation(iz.getTeleportCoord(), r);
						}
					} else {
						// Real CC
						cc.setReflection(r);
						r.setCommandChannel(cc);

						for (Player member : cc) {
							if (iz.getRemovedItemId() > 0)
								ItemFunctions.removeItem(member, iz.getRemovedItemId(), iz.getRemovedItemCount(), true);
							if (iz.getGiveItemId() > 0)
								ItemFunctions.addItem(member, iz.getGiveItemId(), iz.getGiveItemCount(), true);
							if (iz.isDispelBuffs())
								member.dispelBuffs();
							if (iz.getSetReuseUponEntry())
								member.setInstanceReuse(iz.getId(), System.currentTimeMillis());
							member.setVar("backCoords", invoker.getLoc().toXYZString(), -1);
							member.teleToLocation(iz.getTeleportCoord(), r);
						}
					}
				}
				break;
		}

		return r;
	}
}
