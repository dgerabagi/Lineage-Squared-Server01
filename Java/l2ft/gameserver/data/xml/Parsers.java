package l2ft.gameserver.data.xml;

import l2ft.gameserver.data.StringHolder;
import l2ft.gameserver.data.htm.HtmCache;
import l2ft.gameserver.data.xml.holder.BuyListHolder;
import l2ft.gameserver.data.xml.holder.MultiSellHolder;
import l2ft.gameserver.data.xml.holder.ProductHolder;
import l2ft.gameserver.data.xml.holder.RecipeHolder;
import l2ft.gameserver.data.xml.parser.AirshipDockParser;
import l2ft.gameserver.data.xml.parser.ArmorSetsParser;
import l2ft.gameserver.data.xml.parser.CubicParser;
import l2ft.gameserver.data.xml.parser.DomainParser;
import l2ft.gameserver.data.xml.parser.DoorParser;
import l2ft.gameserver.data.xml.parser.EnchantItemParser;
import l2ft.gameserver.data.xml.parser.EventParser;
import l2ft.gameserver.data.xml.parser.FishDataParser;
import l2ft.gameserver.data.xml.parser.HennaParser;
import l2ft.gameserver.data.xml.parser.InstantZoneParser;
import l2ft.gameserver.data.xml.parser.ItemParser;
import l2ft.gameserver.data.xml.parser.NpcParser;
import l2ft.gameserver.data.xml.parser.OptionDataParser;
import l2ft.gameserver.data.xml.parser.PetitionGroupParser;
import l2ft.gameserver.data.xml.parser.ResidenceParser;
import l2ft.gameserver.data.xml.parser.RestartPointParser;
import l2ft.gameserver.data.xml.parser.SkillAcquireParser;
import l2ft.gameserver.data.xml.parser.SoulCrystalParser;
import l2ft.gameserver.data.xml.parser.SpawnParser;
import l2ft.gameserver.data.xml.parser.StaticObjectParser;
import l2ft.gameserver.data.xml.parser.ZoneParser;
import l2ft.gameserver.instancemanager.ReflectionManager;
import l2ft.gameserver.tables.SkillTable;

public abstract class Parsers
{
	public static void parseAll()
	{
		HtmCache.getInstance().reload();
		StringHolder.getInstance().load();
		//
		SkillTable.getInstance().load(); // - SkillParser.getInstance();
		OptionDataParser.getInstance().load();
		ItemParser.getInstance().load();
		//
		NpcParser.getInstance().load();

		DomainParser.getInstance().load();
		RestartPointParser.getInstance().load();

		StaticObjectParser.getInstance().load();
		DoorParser.getInstance().load();
		ZoneParser.getInstance().load();
		SpawnParser.getInstance().load();
		InstantZoneParser.getInstance().load();

		ReflectionManager.getInstance();
		//
		AirshipDockParser.getInstance().load();
		SkillAcquireParser.getInstance().load();
		//
		ResidenceParser.getInstance().load();
		EventParser.getInstance().load();
		// support(cubic & agathion)
		CubicParser.getInstance().load();
		//
		BuyListHolder.getInstance();
		RecipeHolder.getInstance();
		MultiSellHolder.getInstance();
		ProductHolder.getInstance();
		// AgathionParser.getInstance();
		// item support
		HennaParser.getInstance().load();
		EnchantItemParser.getInstance().load();
		SoulCrystalParser.getInstance().load();
		ArmorSetsParser.getInstance().load();
		FishDataParser.getInstance().load();

		// etc
		PetitionGroupParser.getInstance().load();
	}
}
