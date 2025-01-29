package handler.items;

import java.util.Collection;

import l2ft.gameserver.cache.Msg;
import l2ft.gameserver.data.xml.holder.RecipeHolder;
import l2ft.gameserver.model.Playable;
import l2ft.gameserver.model.Player;
import l2ft.gameserver.model.Recipe;
import l2ft.gameserver.model.Skill;
import l2ft.gameserver.model.items.ItemInstance;
import l2ft.gameserver.network.l2.components.SystemMsg;
import l2ft.gameserver.network.l2.s2c.RecipeBookItemList;
import l2ft.gameserver.network.l2.s2c.SystemMessage;


public class Recipes extends ScriptItemHandler
{
	private static int[] _itemIds = null;
	private static final int[] LEVELS = { 0, 5, 20, 28, 36, 43, 49, 55, 62, 70, 82, 84};

	public Recipes()
	{
		Collection<Recipe> rc = RecipeHolder.getInstance().getRecipes();
		_itemIds = new int[rc.size()];
		int i = 0;
		for(Recipe r : rc)
			_itemIds[i++] = r.getRecipeId();
	}

	@Override
	public boolean useItem(Playable playable, ItemInstance item, boolean ctrl)
	{
		if(playable == null || !playable.isPlayer())
			return false;
		Player player = (Player) playable;

		Recipe rp = RecipeHolder.getInstance().getRecipeByRecipeItem(item.getItemId());
		if(rp.isDwarvenRecipe())
		{
			if(player.getDwarvenRecipeLimit() > 0)
			{
				if(player.getDwarvenRecipeBook().size() >= player.getDwarvenRecipeLimit())
				{
					player.sendPacket(Msg.NO_FURTHER_RECIPES_MAY_BE_REGISTERED);
					return false;
				}
				if(player.getLevel() < LEVELS[rp.getLevel()])
				{
					player.sendMessage("You need to have "+LEVELS[rp.getLevel()]+" level to register this recipe!");
					return false;
				}
				if(player.hasRecipe(rp))
				{
					player.sendPacket(Msg.THAT_RECIPE_IS_ALREADY_REGISTERED);
					return false;
				}
				if(!player.getInventory().destroyItem(item, 1L))
				{
					player.sendPacket(SystemMsg.INCORRECT_ITEM_COUNT);
					return false;
				}
				// add recipe to recipebook
				player.registerRecipe(rp, true);
				player.sendPacket(new SystemMessage(SystemMessage.S1_HAS_BEEN_ADDED).addItemName(item.getItemId()));
				player.sendPacket(new RecipeBookItemList(player, true));
				return true;
			}
			else
				player.sendPacket(Msg.YOU_ARE_NOT_AUTHORIZED_TO_REGISTER_A_RECIPE);
		}
		else if(player.getCommonRecipeLimit() > 0)
		{
			if(player.getCommonRecipeBook().size() >= player.getCommonRecipeLimit())
			{
				player.sendPacket(Msg.NO_FURTHER_RECIPES_MAY_BE_REGISTERED);
				return false;
			}
			if(player.hasRecipe(rp))
			{
				player.sendPacket(Msg.THAT_RECIPE_IS_ALREADY_REGISTERED);
				return false;
			}
			if(!player.getInventory().destroyItem(item, 1L))
			{
				player.sendPacket(SystemMsg.INCORRECT_ITEM_COUNT);
				return false;
			}
			player.registerRecipe(rp, true);
			player.sendPacket(new SystemMessage(SystemMessage.S1_HAS_BEEN_ADDED).addItemName(item.getItemId()));
			player.sendPacket(new RecipeBookItemList(player, false));
			return true;
		}
		else
			player.sendPacket(Msg.YOU_ARE_NOT_AUTHORIZED_TO_REGISTER_A_RECIPE);
		return false;
	}

	@Override
	public int[] getItemIds()
	{
		return _itemIds;
	}
}