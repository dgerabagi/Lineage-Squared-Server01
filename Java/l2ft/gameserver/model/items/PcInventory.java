package l2ft.gameserver.model.items;

import java.util.Collection;

import org.apache.commons.lang3.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import l2ft.commons.collections.CollectionUtils;
import l2ft.commons.dao.JdbcEntityState;
import l2ft.commons.threading.RunnableImpl;
import l2ft.gameserver.autofarm.AutoFarmState;
import l2ft.gameserver.autofarm.DropTrackerData;
import l2ft.gameserver.instancemanager.CursedWeaponsManager;
import l2ft.gameserver.model.Player;
import l2ft.gameserver.model.items.Inventory.ItemOrderComparator;
import l2ft.gameserver.model.items.ItemInstance.ItemLocation;
import l2ft.gameserver.model.items.listeners.AccessoryListener;
import l2ft.gameserver.model.items.listeners.ArmorSetListener;
import l2ft.gameserver.model.items.listeners.BowListener;
import l2ft.gameserver.model.items.listeners.ItemAugmentationListener;
import l2ft.gameserver.model.items.listeners.ItemEnchantOptionsListener;
import l2ft.gameserver.model.items.listeners.ItemSkillsListener;
import l2ft.gameserver.network.l2.s2c.ExBR_AgathionEnergyInfo;
import l2ft.gameserver.network.l2.s2c.InventoryUpdate;
import l2ft.gameserver.network.l2.s2c.SystemMessage;
import l2ft.gameserver.taskmanager.DelayedItemsManager;
import l2ft.gameserver.templates.item.EtcItemTemplate.EtcItemType;
import l2ft.gameserver.templates.item.ItemTemplate;
import l2ft.gameserver.utils.ItemFunctions;

/**
 * PcInventory is a specific inventory for player characters, supporting item
 * equip/unequip events, additional checks, logging, and auto-farm tracking.
 */
public class PcInventory extends Inventory {
	private static final Logger _pcInvLog = LoggerFactory.getLogger(PcInventory.class);

	private final Player _owner;

	// Locking system
	private LockType _lockType = LockType.NONE;
	private int[] _lockItems = ArrayUtils.EMPTY_INT_ARRAY;

	public PcInventory(Player owner) {
		super(owner.getObjectId());
		_owner = owner;

		addListener(ItemSkillsListener.getInstance());
		addListener(ItemAugmentationListener.getInstance());
		addListener(ItemEnchantOptionsListener.getInstance());
		addListener(ArmorSetListener.getInstance());
		addListener(BowListener.getInstance());
		addListener(AccessoryListener.getInstance());
	}

	@Override
	public Player getActor() {
		return _owner;
	}

	@Override
	protected ItemLocation getBaseLocation() {
		return ItemLocation.INVENTORY;
	}

	@Override
	protected ItemLocation getEquipLocation() {
		return ItemLocation.PAPERDOLL;
	}

	/**
	 * @return how many adena the player has.
	 */
	public long getAdena() {
		ItemInstance _adena = getItemByItemId(57);
		return _adena == null ? 0 : _adena.getCount();
	}

	/**
	 * Adds adena to the player's inventory.
	 *
	 * @param amount how many adena to add
	 * @return the updated adena item instance
	 */
	public ItemInstance addAdena(long amount) {
		return addItem(ItemTemplate.ITEM_ID_ADENA, amount);
	}

	/**
	 * Reduces the specified adena amount from inventory.
	 *
	 * @param adena how many adena to remove
	 * @return true if successful
	 */
	public boolean reduceAdena(long adena) {
		return destroyItemByItemId(ItemTemplate.ITEM_ID_ADENA, adena);
	}

	/**
	 * Gets augmentation ID for item in the given slot.
	 *
	 * @param slot paperdoll slot
	 * @return augmentation ID, or 0 if none
	 */
	public int getPaperdollAugmentationId(int slot) {
		ItemInstance item = _paperdoll[slot];
		return (item != null && item.isAugmented()) ? item.getAugmentationId() : 0;
	}

	@Override
	public int getPaperdollItemId(int slot) {
		Player player = getActor();
		int itemId = super.getPaperdollItemId(slot);

		// Display Airship helm if clan airship driver
		if (slot == PAPERDOLL_RHAND && itemId == 0 && player.isClanAirShipDriver())
			itemId = 13556; // Airship Helm

		return itemId;
	}

	@Override
	protected void onRefreshWeight() {
		// notify the owner about possible overload changes
		getActor().refreshOverloaded();
	}

	/**
	 * Validate the equipped items, forcibly unequips items that cannot be worn.
	 */
	public void validateItems() {
		for (ItemInstance item : _paperdoll) {
			if (item == null)
				continue;

			if (ItemFunctions.checkIfCanEquip(getActor(), item) != null
					|| !item.getTemplate().testCondition(getActor(), item)) {
				unEquipItem(item);
				getActor().sendDisarmMessage(item);
			}
		}
	}

	/**
	 * Check item-based skills for penalty or triggers.
	 */
	public void validateItemsSkills() {
		for (ItemInstance item : _paperdoll) {
			if (item == null)
				continue;
			if (item.getTemplate().getType2() != ItemTemplate.TYPE2_WEAPON)
				continue;

			boolean needUnequipSkills = getActor().getWeaponsExpertisePenalty() > 0;

			// attached skill array
			if (item.getTemplate().getAttachedSkills().length > 0) {
				boolean has = getActor().getSkillLevel(item.getTemplate().getAttachedSkills()[0].getId()) > 0;
				if (needUnequipSkills && has)
					ItemSkillsListener.getInstance().onUnequip(item.getEquipSlot(), item, getActor());
				else if (!needUnequipSkills && !has)
					ItemSkillsListener.getInstance().onEquip(item.getEquipSlot(), item, getActor());
			}
			// enchant-4 skill
			else if (item.getTemplate().getEnchant4Skill() != null) {
				boolean has = getActor().getSkillLevel(item.getTemplate().getEnchant4Skill().getId()) > 0;
				if (needUnequipSkills && has)
					ItemSkillsListener.getInstance().onUnequip(item.getEquipSlot(), item, getActor());
				else if (!needUnequipSkills && !has)
					ItemSkillsListener.getInstance().onEquip(item.getEquipSlot(), item, getActor());
			}
			// triggers
			else if (!item.getTemplate().getTriggerList().isEmpty()) {
				if (needUnequipSkills)
					ItemSkillsListener.getInstance().onUnequip(item.getEquipSlot(), item, getActor());
				else
					ItemSkillsListener.getInstance().onEquip(item.getEquipSlot(), item, getActor());
			}
		}
	}

	/**
	 * HACK: forcibly re-apply item-based skills after sub-class changes
	 */
	public boolean isRefresh = false;

	public void refreshEquip() {
		isRefresh = true;
		for (ItemInstance item : getItems()) {
			if (item.isEquipped()) {
				int slot = item.getEquipSlot();
				_listeners.onUnequip(slot, item);
				_listeners.onEquip(slot, item);
			} else if (item.getItemType() == EtcItemType.RUNE) {
				_listeners.onUnequip(-1, item);
				_listeners.onEquip(-1, item);
			}
		}
		isRefresh = false;
	}

	/**
	 * Sort items by their "locData" field based on the given order array.
	 *
	 * @param order two-dimensional array: [objectId, newLocData]
	 */
	public void sort(int[][] order) {
		boolean needSort = false;
		for (int[] element : order) {
			ItemInstance item = getItemByObjectId(element[0]);
			if (item == null)
				continue;
			if (item.getLocation() != ItemLocation.INVENTORY)
				continue;

			if (item.getLocData() == element[1])
				continue;

			item.setLocData(element[1]);
			item.setJdbcState(JdbcEntityState.UPDATED); // lazy update
			needSort = true;
		}
		if (needSort)
			CollectionUtils.eqSort(_items, ItemOrderComparator.getInstance());
	}

	private static final int[][] arrows = {
			// NG, D, C, B, A, S
			{ 17 }, // NG
			{ 1341, 22067 }, // D
			{ 1342, 22068 }, // C
			{ 1343, 22069 }, // B
			{ 1344, 22070 }, // A
			{ 1345, 22071 }, // S
	};

	public ItemInstance findArrowForBow(ItemTemplate bow) {
		int[] arrowsId = arrows[bow.getCrystalType().externalOrdinal];
		ItemInstance ret = null;
		for (int id : arrowsId) {
			if ((ret = getItemByItemId(id)) != null)
				return ret;
		}
		return null;
	}

	private static final int[][] bolts = {
			// NG, D, C, B, A, S
			{ 9632 }, // NG
			{ 9633, 22144 }, // D
			{ 9634, 22145 }, // C
			{ 9635, 22146 }, // B
			{ 9636, 22147 }, // A
			{ 9637, 22148 }, // S
	};

	public ItemInstance findArrowForCrossbow(ItemTemplate xbow) {
		int[] boltsId = bolts[xbow.getCrystalType().externalOrdinal];
		ItemInstance ret = null;
		for (int id : boltsId) {
			if ((ret = getItemByItemId(id)) != null)
				return ret;
		}
		return null;
	}

	public ItemInstance findEquippedLure() {
		ItemInstance res = null;
		int last_lure = 0;
		Player owner = getActor();
		String LastLure = owner.getVar("LastLure");
		if (LastLure != null && !LastLure.isEmpty())
			last_lure = Integer.parseInt(LastLure);

		for (ItemInstance temp : getItems()) {
			if (temp.getItemType() == EtcItemType.BAIT) {
				if (temp.getLocation() == ItemLocation.PAPERDOLL && temp.getEquipSlot() == PAPERDOLL_LHAND)
					return temp;
				else if (last_lure > 0 && res == null && temp.getObjectId() == last_lure)
					res = temp;
			}
		}
		return res;
	}

	/**
	 * Sets a lock on certain items.
	 *
	 * @param lock  The type of lock (INCLUDE / EXCLUDE)
	 * @param items Which item IDs are locked
	 */
	public void lockItems(LockType lock, int[] items) {
		if (_lockType != LockType.NONE)
			return;

		_lockType = lock;
		_lockItems = items;

		getActor().sendItemList(false);
	}

	public void unlock() {
		if (_lockType == LockType.NONE)
			return;

		_lockType = LockType.NONE;
		_lockItems = ArrayUtils.EMPTY_INT_ARRAY;

		getActor().sendItemList(false);
	}

	public boolean isLockedItem(ItemInstance item) {
		switch (_lockType) {
			case INCLUDE:
				return ArrayUtils.contains(_lockItems, item.getItemId());
			case EXCLUDE:
				return !ArrayUtils.contains(_lockItems, item.getItemId());
			default:
				return false;
		}
	}

	public LockType getLockType() {
		return _lockType;
	}

	public int[] getLockItems() {
		return _lockItems;
	}

	@Override
	protected void onRestoreItem(ItemInstance item) {
		super.onRestoreItem(item);

		if (item.getItemType() == EtcItemType.RUNE)
			_listeners.onEquip(-1, item);

		if (item.isTemporalItem())
			item.startTimer(new LifeTimeTask(item));

		if (item.isCursed())
			CursedWeaponsManager.getInstance().checkPlayer(getActor(), item);
	}

	@Override
	protected void onAddItem(ItemInstance item) {
		// Perform the standard logic
		super.onAddItem(item);

		// If it's a rune, apply immediate "onEquip(-1)"
		if (item.getItemType() == EtcItemType.RUNE) {
			_listeners.onEquip(-1, item);
		}

		// If it's a temporary item, start the lifetime task
		if (item.isTemporalItem()) {
			item.startTimer(new LifeTimeTask(item));
		}

		// If it's cursed, check
		if (item.isCursed()) {
			CursedWeaponsManager.getInstance().checkPlayer(getActor(), item);
		}
	}

	@Override
	protected void onRemoveItem(ItemInstance item) {
		super.onRemoveItem(item);

		// remove from shortcuts
		getActor().removeItemFromShortCut(item.getObjectId());

		if (item.getItemType() == EtcItemType.RUNE)
			_listeners.onUnequip(-1, item);

		if (item.isTemporalItem())
			item.stopTimer();
	}

	@Override
	protected void onEquip(int slot, ItemInstance item) {
		super.onEquip(slot, item);

		if (item.isShadowItem())
			item.startTimer(new ShadowLifeTimeTask(item));
	}

	@Override
	protected void onUnequip(int slot, ItemInstance item) {
		super.onUnequip(slot, item);

		if (item.isShadowItem())
			item.stopTimer();
	}

	@Override
	public void restore() {
		final int ownerId = getOwnerId();

		writeLock();
		try {
			Collection<ItemInstance> items = _itemsDAO.getItemsByOwnerIdAndLoc(ownerId, getBaseLocation());
			for (ItemInstance item : items) {
				_items.add(item);
				onRestoreItem(item);
			}
			CollectionUtils.eqSort(_items, ItemOrderComparator.getInstance());

			items = _itemsDAO.getItemsByOwnerIdAndLoc(ownerId, getEquipLocation());
			for (ItemInstance item : items) {
				_items.add(item);
				onRestoreItem(item);
				if (item.getEquipSlot() >= PAPERDOLL_MAX) {
					// revert invalid equip slot
					item.setLocation(getBaseLocation());
					item.setLocData(0);
					item.setEquipped(false);
					continue;
				}
				setPaperdollItem(item.getEquipSlot(), item);
			}
		} finally {
			writeUnlock();
		}

		DelayedItemsManager.getInstance().loadDelayed(getActor(), false);
		refreshWeight();
	}

	@Override
	public void store() {
		writeLock();
		try {
			_itemsDAO.update(_items);
		} finally {
			writeUnlock();
		}
	}

	@Override
	protected void sendAddItem(ItemInstance item) {
		Player actor = getActor();

		actor.sendPacket(new InventoryUpdate().addNewItem(item));
		if (item.getTemplate().getAgathionEnergy() > 0)
			actor.sendPacket(new ExBR_AgathionEnergyInfo(1, item));
	}

	@Override
	protected void sendModifyItem(ItemInstance item) {
		Player actor = getActor();

		actor.sendPacket(new InventoryUpdate().addModifiedItem(item));
		if (item.getTemplate().getAgathionEnergy() > 0)
			actor.sendPacket(new ExBR_AgathionEnergyInfo(1, item));
	}

	@Override
	protected void sendRemoveItem(ItemInstance item) {
		getActor().sendPacket(new InventoryUpdate().addRemovedItem(item));
	}

	/**
	 * No-op placeholder for potential "start timers" logic.
	 */
	public void startTimers() {
		// no-op
	}

	/**
	 * Stops all shadow/temporal item timers.
	 */
	public void stopAllTimers() {
		for (ItemInstance item : getItems()) {
			if (item.isShadowItem() || item.isTemporalItem())
				item.stopTimer();
		}
	}

	/**
	 * Task for shadow item lifetime management.
	 */
	protected class ShadowLifeTimeTask extends RunnableImpl {
		private ItemInstance item;

		ShadowLifeTimeTask(ItemInstance item) {
			this.item = item;
		}

		@Override
		public void runImpl() throws Exception {
			Player player = getActor();

			if (!item.isEquipped())
				return;

			int mana;
			synchronized (item) {
				item.setLifeTime(item.getLifeTime() - 1);
				mana = item.getShadowLifeTime();
				if (mana <= 0)
					destroyItem(item);
			}

			SystemMessage sm = null;
			if (mana == 10)
				sm = new SystemMessage(SystemMessage.S1S_REMAINING_MANA_IS_NOW_10);
			else if (mana == 5)
				sm = new SystemMessage(SystemMessage.S1S_REMAINING_MANA_IS_NOW_5);
			else if (mana == 1)
				sm = new SystemMessage(SystemMessage.S1S_REMAINING_MANA_IS_NOW_1_IT_WILL_DISAPPEAR_SOON);
			else if (mana <= 0)
				sm = new SystemMessage(SystemMessage.S1S_REMAINING_MANA_IS_NOW_0_AND_THE_ITEM_HAS_DISAPPEARED);
			else {
				player.sendPacket(new InventoryUpdate().addModifiedItem(item));
			}

			if (sm != null) {
				sm.addItemName(item.getItemId());
				player.sendPacket(sm);
			}
		}
	}

	/**
	 * Task for limited-time item expiration.
	 */
	protected class LifeTimeTask extends RunnableImpl {
		private ItemInstance item;

		LifeTimeTask(ItemInstance item) {
			this.item = item;
		}

		@Override
		public void runImpl() throws Exception {
			Player player = getActor();
			int left;

			synchronized (item) {
				left = item.getTemporalLifeTime();
				if (left <= 0)
					destroyItem(item);
			}

			if (left <= 0) {
				player.sendPacket(
						new SystemMessage(SystemMessage.THE_LIMITED_TIME_ITEM_HAS_BEEN_DELETED)
								.addItemName(item.getItemId()));
			}
		}
	}
}
