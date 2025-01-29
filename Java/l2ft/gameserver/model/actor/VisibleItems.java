package l2ft.gameserver.model.actor;

import l2ft.gameserver.model.Player;
import l2ft.gameserver.model.items.ItemInstance;
import l2ft.gameserver.templates.item.WeaponTemplate.WeaponType;

public class VisibleItems 
{
	
	public static int CHANGING_LOOK_ITEM_ID = 6657;
	public static int getVisibleWeaponId(ItemInstance item, Player player)
	{
		if(item.isWeapon() && player.getInventory().getItemByItemId(CHANGING_LOOK_ITEM_ID) != null)
		{
			WeaponType type = ((WeaponType)item.getItemType());
			switch(type)
			{
			case ANCIENTSWORD:
				break;
			case BIGBLUNT:
				break;
			case BIGSWORD:
				break;
			case BLUNT:
				break;
			case BOW:
				break;
			case CROSSBOW:
				break;
			case DAGGER:
				break;
			case DUAL:
				break;
			case DUALDAGGER:
				break;
			case DUALFIST:
				break;
			case ETC:
				break;
			case FIST:
				break;
			case NONE:
				break;
			case PET:
				break;
			case POLE:
				break;
			case RAPIER:
				break;
			case ROD:
				break;
			case SWORD:
				break;
			default:
				break;
			}
		}
		return item.getItemId();
	}
}
