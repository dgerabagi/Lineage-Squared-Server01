// java/l2ft/gameserver/network/l2/c2s/RequestEnchantItem.java
package l2ft.gameserver.network.l2.c2s;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import l2ft.commons.dao.JdbcEntityState;
import l2ft.commons.dbutils.DbUtils;
import l2ft.commons.util.Rnd;
import l2ft.gameserver.Config;
import l2ft.gameserver.data.xml.holder.EnchantItemHolder;
import l2ft.gameserver.database.DatabaseFactory;
import l2ft.gameserver.model.Player;
import l2ft.gameserver.model.actor.instances.player.Bonus;
import l2ft.gameserver.model.items.ItemInstance;
import l2ft.gameserver.model.items.PcInventory;
import l2ft.gameserver.network.l2.components.SystemMsg;
import l2ft.gameserver.network.l2.s2c.EnchantResult;
import l2ft.gameserver.network.l2.s2c.InventoryUpdate;
import l2ft.gameserver.network.l2.s2c.MagicSkillUse;
import l2ft.gameserver.stats.Stats;
import l2ft.gameserver.templates.item.ItemTemplate;
import l2ft.gameserver.templates.item.ItemTemplate.Grade;
import l2ft.gameserver.templates.item.support.EnchantScroll;
import l2ft.gameserver.utils.ItemFunctions;
import l2ft.gameserver.utils.Log;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RequestEnchantItem extends L2GameClientPacket {
	private static final Logger _log = LoggerFactory.getLogger(RequestEnchantItem.class);
	private int _objectId, _catalystObjId;

	@Override
	protected void readImpl() {
		_objectId = readD();
		_catalystObjId = readD();
	}

	@Override
	protected void runImpl() {
		Player player = getClient().getActiveChar();
		if (player == null)
			return;

		if (player.isActionsDisabled()) {
			player.setEnchantScroll(null);
			player.sendActionFailed();
			return;
		}

		if (player.isInTrade()) {
			player.setEnchantScroll(null);
			player.sendActionFailed();
			return;
		}

		if (player.isInStoreMode()) {
			player.setEnchantScroll(null);
			player.sendPacket(EnchantResult.CANCEL);
			player.sendPacket(SystemMsg.YOU_CANNOT_ENCHANT_WHILE_OPERATING_A_PRIVATE_STORE_OR_PRIVATE_WORKSHOP);
			player.sendActionFailed();
			return;
		}

		PcInventory inventory = player.getInventory();
		inventory.writeLock();
		try {
			ItemInstance item = inventory.getItemByObjectId(_objectId);
			ItemInstance catalyst = _catalystObjId > 0 ? inventory.getItemByObjectId(_catalystObjId) : null;
			ItemInstance scroll = player.getEnchantScroll();

			if (item == null || scroll == null) {
				player.sendActionFailed();
				return;
			}

			if (item.isAccessory()
					&& player.calcStat(Stats.REFLECT_AND_BLOCK_MSKILL_DAMAGE_CHANCE, 0, player, null) > 0)
				return;

			EnchantScroll enchantScroll = EnchantItemHolder.getInstance().getEnchantScroll(scroll.getItemId());
			if (enchantScroll == null) {
				// Use the old enchant method if the enchant scroll is not found
				doEnchantOld(player, item, scroll, catalyst);
				return;
			}

			if (enchantScroll.getMaxEnchant() != -1 && item.getEnchantLevel() >= enchantScroll.getMaxEnchant()) {
				player.sendPacket(EnchantResult.CANCEL);
				player.sendPacket(SystemMsg.INAPPROPRIATE_ENCHANT_CONDITIONS);
				player.sendActionFailed();
				return;
			}

			if (enchantScroll.getItems().size() > 0) {
				if (!enchantScroll.getItems().contains(item.getItemId())) {
					player.sendPacket(EnchantResult.CANCEL);
					player.sendPacket(SystemMsg.DOES_NOT_FIT_STRENGTHENING_CONDITIONS_OF_THE_SCROLL);
					player.sendActionFailed();
					return;
				}
			} else {
				if (!enchantScroll.getGrades().contains(item.getCrystalType())) {
					player.sendPacket(EnchantResult.CANCEL);
					player.sendPacket(SystemMsg.DOES_NOT_FIT_STRENGTHENING_CONDITIONS_OF_THE_SCROLL);
					player.sendActionFailed();
					return;
				}
			}

			if (!item.canBeEnchanted(false)) {
				player.sendPacket(EnchantResult.CANCEL);
				player.sendPacket(SystemMsg.INAPPROPRIATE_ENCHANT_CONDITIONS);
				player.sendActionFailed();
				return;
			}

			if (!inventory.destroyItem(scroll, 1L) || catalyst != null && !inventory.destroyItem(catalyst, 1L)) {
				player.sendPacket(EnchantResult.CANCEL);
				player.sendActionFailed();
				return;
			}

			boolean equipped = false;
			if (equipped = item.isEquipped())
				inventory.unEquipItem(item);

			int safeEnchantLevel = item.getTemplate().getBodyPart() == ItemTemplate.SLOT_FULL_ARMOR ? 4 : 3;

			int chance = enchantScroll.getChance();
			if (item.getEnchantLevel() < safeEnchantLevel)
				chance = 100;

			if (Rnd.chance(chance)) {
				item.setEnchantLevel(item.getEnchantLevel() + 1);
				item.setJdbcState(JdbcEntityState.UPDATED);
				item.update();

				// Log the enchantment success
				logEnchantmentEvent(player, item, "enchant_success");

				if (equipped)
					inventory.equipItem(item);

				player.sendPacket(new InventoryUpdate().addModifiedItem(item));
				player.sendPacket(EnchantResult.SUCESS);

				if (enchantScroll.isHasVisualEffect() && item.getEnchantLevel() > 3)
					player.broadcastPacket(new MagicSkillUse(player, player, 5965, 1, 500, 1500));
			} else {
				String action = "";
				switch (enchantScroll.getResultType()) {
					case CRYSTALS:
						if (item.isEquipped())
							player.sendDisarmMessage(item);

						Log.LogItem(player, Log.EnchantFail, item);

						if (!inventory.destroyItem(item, 1L)) {
							player.sendActionFailed();
							return;
						}

						int crystalId = item.getCrystalType().cry;
						if (crystalId > 0 && item.getTemplate().getCrystalCount() > 0) {
							int crystalAmount = (int) (item.getTemplate().getCrystalCount() * 0.87);
							if (item.getEnchantLevel() > 3)
								crystalAmount += item.getTemplate().getCrystalCount() * 0.25
										* (item.getEnchantLevel() - 3);
							if (crystalAmount < 1)
								crystalAmount = 1;

							player.sendPacket(new EnchantResult(1, crystalId, crystalAmount));
							ItemFunctions.addItem(player, crystalId, crystalAmount, true);
						} else
							player.sendPacket(EnchantResult.FAILED_NO_CRYSTALS);

						if (enchantScroll.isHasVisualEffect())
							player.broadcastPacket(new MagicSkillUse(player, player, 5949, 1, 500, 1500));
						action = "item_broken";
						break;
					case DROP_ENCHANT:
						item.setEnchantLevel(0);
						item.setJdbcState(JdbcEntityState.UPDATED);
						item.update();

						if (equipped)
							inventory.equipItem(item);

						player.sendPacket(new InventoryUpdate().addModifiedItem(item));
						player.sendPacket(SystemMsg.THE_BLESSED_ENCHANT_FAILED);
						player.sendPacket(EnchantResult.BLESSED_FAILED);
						action = "enchant_failure";
						break;
					case NOTHING:
						player.sendPacket(EnchantResult.ANCIENT_FAILED);
						action = "enchant_no_change";
						break;
				}
				// Log the enchantment failure
				logEnchantmentEvent(player, item, action);
			}
		} finally {
			inventory.writeUnlock();
			player.setEnchantScroll(null);
			player.updateStats();
		}
	}

	private void logEnchantmentEvent(Player player, ItemInstance item, String action) {
		// We'll remove details parameter entirely and store item_type and item_grade
		Connection con = null;
		PreparedStatement statement = null;
		try {
			con = DatabaseFactory.getInstance().getConnection();
			statement = con.prepareStatement(
					"INSERT INTO enchantment_history (item_object_id, item_id, item_name, owner_id, enchant_level, action, item_type, item_grade) VALUES (?, ?, ?, ?, ?, ?, ?, ?)");
			statement.setInt(1, item.getObjectId());
			statement.setInt(2, item.getItemId());
			statement.setString(3, item.getTemplate().getName());
			statement.setInt(4, player.getObjectId());
			statement.setInt(5, item.getEnchantLevel());
			statement.setString(6, action);

			// Extract item_type and item_grade
			String itemType = item.getTemplate().getItemType().toString();
			String itemGrade = item.getTemplate().getItemGrade().toString();

			statement.setString(7, itemType);
			statement.setString(8, itemGrade);

			statement.executeUpdate();
		} catch (SQLException e) {
			_log.error("Failed to log enchantment event for item: " + item + ", action: " + action, e);
		} finally {
			DbUtils.closeQuietly(con, statement);
		}
	}

	private void doEnchantOld(Player player, ItemInstance item, ItemInstance scroll, ItemInstance catalyst) {
		PcInventory inventory = player.getInventory();
		if (!ItemFunctions.checkCatalyst(item, catalyst))
			catalyst = null;

		if (!item.canBeEnchanted(true)) {
			player.sendPacket(EnchantResult.CANCEL);
			player.sendPacket(SystemMsg.INAPPROPRIATE_ENCHANT_CONDITIONS);
			player.sendActionFailed();
			return;
		}

		int crystalId = ItemFunctions.getEnchantCrystalId(item, scroll, catalyst);

		if (crystalId == -1) {
			player.sendPacket(EnchantResult.CANCEL);
			player.sendPacket(SystemMsg.DOES_NOT_FIT_STRENGTHENING_CONDITIONS_OF_THE_SCROLL);
			player.sendActionFailed();
			return;
		}

		int scrollId = scroll.getItemId();

		// Additional checks can be added here if necessary

		if (!inventory.destroyItem(scroll, 1L) || catalyst != null && !inventory.destroyItem(catalyst, 1L)) {
			player.sendPacket(EnchantResult.CANCEL);
			player.sendActionFailed();
			return;
		}

		boolean equipped = false;
		if (equipped = item.isEquipped())
			inventory.unEquipItem(item);

		int chance = calculateOldEnchantChance(player, item, scroll, scrollId, catalyst);

		if (Rnd.chance(chance)) {
			item.setEnchantLevel(item.getEnchantLevel() + 1);
			item.setJdbcState(JdbcEntityState.UPDATED);
			item.update();

			// Log the enchantment success
			logEnchantmentEvent(player, item, "enchant_success");

			if (equipped)
				inventory.equipItem(item);

			player.sendPacket(new InventoryUpdate().addModifiedItem(item));
			player.sendPacket(EnchantResult.SUCESS);

			// Visual effects can be added here
		} else {
			String action = "";
			if (ItemFunctions.isBlessedEnchantScroll(scrollId)) {
				item.setEnchantLevel(Config.SAFE_ENCHANT_LVL);
				item.setJdbcState(JdbcEntityState.UPDATED);
				item.update();

				if (equipped)
					inventory.equipItem(item);

				player.sendPacket(new InventoryUpdate().addModifiedItem(item));
				player.sendPacket(SystemMsg.THE_BLESSED_ENCHANT_FAILED);
				player.sendPacket(EnchantResult.BLESSED_FAILED);
				action = "enchant_failure";
			} else if (ItemFunctions.isAncientEnchantScroll(scrollId)
					|| ItemFunctions.isDestructionWpnEnchantScroll(scrollId)
					|| ItemFunctions.isDestructionArmEnchantScroll(scrollId)) {
				player.sendPacket(EnchantResult.ANCIENT_FAILED);
				action = "enchant_no_change";
			} else {
				if (item.isEquipped())
					player.sendDisarmMessage(item);

				Log.LogItem(player, Log.EnchantFail, item);

				if (!inventory.destroyItem(item, 1L)) {
					player.sendActionFailed();
					return;
				}

				if (crystalId > 0 && item.getTemplate().getCrystalCount() > 0) {
					int crystalAmount = (int) (item.getTemplate().getCrystalCount() * 0.87);
					if (item.getEnchantLevel() > 3)
						crystalAmount += item.getTemplate().getCrystalCount() * 0.25 * (item.getEnchantLevel() - 3);
					if (crystalAmount < 1)
						crystalAmount = 1;

					player.sendPacket(new EnchantResult(1, crystalId, crystalAmount));
					ItemFunctions.addItem(player, crystalId, crystalAmount, true);
				} else
					player.sendPacket(EnchantResult.FAILED_NO_CRYSTALS);

				// Visual effects can be added here

				action = "item_broken";
			}
			// Log the enchantment failure
			logEnchantmentEvent(player, item, action);
		}
	}

	private int calculateOldEnchantChance(Player player, ItemInstance item, ItemInstance scroll, int scrollId,
			ItemInstance catalyst) {
		// This method calculates the enchant chance based on various factors.
		// You should implement the logic according to your server's configuration.

		int itemType = item.getTemplate().getType2();
		int chance = 0;

		int safeEnchantLevel = item.getTemplate().getBodyPart() == ItemTemplate.SLOT_FULL_ARMOR
				? Config.SAFE_ENCHANT_FULL_BODY
				: Config.SAFE_ENCHANT_COMMON;

		if (item.getEnchantLevel() < safeEnchantLevel)
			return 100;

		if (itemType == ItemTemplate.TYPE2_WEAPON) {
			if (ItemFunctions.isBlessedEnchantScroll(scrollId))
				chance = Config.ENCHANT_CHANCE_WEAPON_BLESS;
			else
				chance = ItemFunctions.isCrystallEnchantScroll(scrollId)
						? Config.ENCHANT_CHANCE_CRYSTAL_WEAPON
						: Config.ENCHANT_CHANCE_WEAPON;
		} else if (itemType == ItemTemplate.TYPE2_SHIELD_ARMOR) {
			if (ItemFunctions.isBlessedEnchantScroll(scrollId))
				chance = Config.ENCHANT_CHANCE_ARMOR_BLESS;
			else
				chance = ItemFunctions.isCrystallEnchantScroll(scrollId)
						? Config.ENCHANT_CHANCE_CRYSTAL_ARMOR
						: Config.ENCHANT_CHANCE_ARMOR;
		} else if (itemType == ItemTemplate.TYPE2_ACCESSORY) {
			if (ItemFunctions.isBlessedEnchantScroll(scrollId))
				chance = Config.ENCHANT_CHANCE_ACCESSORY_BLESS;
			else
				chance = ItemFunctions.isCrystallEnchantScroll(scrollId)
						? Config.ENCHANT_CHANCE_CRYSTAL_ACCESSORY
						: Config.ENCHANT_CHANCE_ACCESSORY;
		} else {
			player.sendPacket(EnchantResult.CANCEL);
			player.sendActionFailed();
			return 0;
		}

		if (ItemFunctions.isDivineEnchantScroll(scrollId))
			chance = 100;
		else if (ItemFunctions.isItemMallEnchantScroll(scrollId))
			chance += 10;

		if (catalyst != null)
			chance += ItemFunctions.getCatalystPower(catalyst.getItemId());

		return chance;
	}
}
// EOF Java/l2ft/gameserver/network/l2/c2s/RequestEnchantItem.java
