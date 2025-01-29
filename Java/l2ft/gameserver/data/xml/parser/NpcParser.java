package l2ft.gameserver.data.xml.parser;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import l2ft.commons.data.xml.AbstractDirParser;
import l2ft.gameserver.Config;
import l2ft.gameserver.data.xml.holder.NpcHolder;
import l2ft.gameserver.model.Skill;
import l2ft.gameserver.model.TeleportLocation;
import l2ft.gameserver.model.base.ClassId;
import l2ft.gameserver.model.base.Element;
import l2ft.gameserver.model.reward.RewardData;
import l2ft.gameserver.model.reward.RewardGroup;
import l2ft.gameserver.model.reward.RewardList;
import l2ft.gameserver.model.reward.RewardType;
import l2ft.gameserver.tables.SkillTable;
import l2ft.gameserver.templates.StatsSet;
import l2ft.gameserver.templates.npc.AbsorbInfo;
import l2ft.gameserver.templates.npc.Faction;
import l2ft.gameserver.templates.npc.MinionData;
import l2ft.gameserver.templates.npc.NpcTemplate;
import l2ft.gameserver.utils.Location;

public final class NpcParser extends AbstractDirParser<NpcHolder>
{
	private static final NpcParser _instance = new NpcParser();

	public static NpcParser getInstance()
	{
		return _instance;
	}

	private NpcParser()
	{
		super(NpcHolder.getInstance());
	}

	@Override
	public File getXMLDir()
	{
		return new File(Config.DATAPACK_ROOT, "data/xml/npc/");
	}

	@Override
	public boolean isIgnored(File f)
	{
		return false;
	}

	@Override
	public String getDTDFileName()
	{
		return "npc.dtd";
	}

	@Override
	protected void readData(org.dom4j.Element rootElement) throws Exception
	{
		for(Iterator<org.dom4j.Element> npcIterator = rootElement.elementIterator(); npcIterator.hasNext();)
		{
			org.dom4j.Element npcElement = npcIterator.next();
			int npcId = Integer.parseInt(npcElement.attributeValue("id"));
			int templateId = npcElement.attributeValue("template_id") == null ? 0 : Integer.parseInt(npcElement.attributeValue("template_id"));
			String name = npcElement.attributeValue("name");
			String title = npcElement.attributeValue("title");

			StatsSet set = new StatsSet();
			set.set("npcId", npcId);
			set.set("displayId", templateId);
			set.set("name", name);
			set.set("title", title);
			set.set("baseCpReg", 0);
			set.set("baseCpMax", 0);

			for(Iterator<org.dom4j.Element> firstIterator = npcElement.elementIterator(); firstIterator.hasNext();)
			{
				org.dom4j.Element firstElement = firstIterator.next();
				if(firstElement.getName().equalsIgnoreCase("set"))
				{
					set.set(firstElement.attributeValue("name"), firstElement.attributeValue("value"));
				}
				else if(firstElement.getName().equalsIgnoreCase("equip"))
				{
					for(Iterator<org.dom4j.Element> eIterator = firstElement.elementIterator(); eIterator.hasNext();)
					{
						org.dom4j.Element eElement = eIterator.next();
						set.set(eElement.getName(), eElement.attributeValue("item_id"));
					}
				}
				else if(firstElement.getName().equalsIgnoreCase("ai_params"))
				{
					StatsSet ai = new StatsSet();
					for(Iterator<org.dom4j.Element> eIterator = firstElement.elementIterator(); eIterator.hasNext();)
					{
						org.dom4j.Element eElement = eIterator.next();
						ai.set(eElement.attributeValue("name"), eElement.attributeValue("value"));
					}
					set.set("aiParams", ai);
				}
				else if(firstElement.getName().equalsIgnoreCase("attributes"))
				{
					int[] attributeAttack = new int[6];
					int[] attributeDefence = new int[6];
					for(Iterator<org.dom4j.Element> eIterator = firstElement.elementIterator(); eIterator.hasNext();)
					{
						org.dom4j.Element eElement = eIterator.next();
						Element element;
						if(eElement.getName().equalsIgnoreCase("defence"))
						{
							element = Element.getElementByName(eElement.attributeValue("attribute"));
							attributeDefence[element.getId()] = Integer.parseInt(eElement.attributeValue("value"));
						}
						else if(eElement.getName().equalsIgnoreCase("attack"))
						{
							element = Element.getElementByName(eElement.attributeValue("attribute"));
							attributeAttack[element.getId()] = Integer.parseInt(eElement.attributeValue("value"));
						}
					}

					set.set("baseAttributeAttack", attributeAttack);
					set.set("baseAttributeDefence", attributeDefence);
				}
			}
			
			multStats(set);

			NpcTemplate template = new NpcTemplate(set);

			for(Iterator<org.dom4j.Element> secondIterator = npcElement.elementIterator(); secondIterator.hasNext();)
			{
				org.dom4j.Element secondElement = secondIterator.next();
				String nodeName = secondElement.getName();
				if(nodeName.equalsIgnoreCase("faction"))
				{
					String factionId = secondElement.attributeValue("name");
					Faction faction = new Faction(factionId);
					int factionRange = Integer.parseInt(secondElement.attributeValue("range"));
					faction.setRange(factionRange);
					for(Iterator<org.dom4j.Element> nextIterator = secondElement.elementIterator(); nextIterator.hasNext();)
					{
						final org.dom4j.Element nextElement = nextIterator.next();
						int ignoreId = Integer.parseInt(nextElement.attributeValue("npc_id"));
						faction.addIgnoreNpcId(ignoreId);
					}
					template.setFaction(faction);
				}
				else if(nodeName.equalsIgnoreCase("rewardlist"))
				{
					RewardType type = RewardType.valueOf(secondElement.attributeValue("type"));
					boolean autoLoot = secondElement.attributeValue("auto_loot") != null && Boolean.parseBoolean(secondElement.attributeValue("auto_loot"));
					RewardList list = new RewardList(type, autoLoot);

					for(Iterator<org.dom4j.Element> nextIterator = secondElement.elementIterator(); nextIterator.hasNext();)
					{
						final org.dom4j.Element nextElement = nextIterator.next();
						final String nextName = nextElement.getName();
						if(nextName.equalsIgnoreCase("group"))
						{
							double enterChance = nextElement.attributeValue("chance") == null ? RewardList.MAX_CHANCE : Double.parseDouble(nextElement.attributeValue("chance")) * 10000;

							RewardGroup group = (type == RewardType.SWEEP || type == RewardType.NOT_RATED_NOT_GROUPED) ? null : new RewardGroup(enterChance);
							for(Iterator<org.dom4j.Element> rewardIterator = nextElement.elementIterator(); rewardIterator.hasNext();)
							{
								org.dom4j.Element rewardElement = rewardIterator.next();
								RewardData data = parseReward(rewardElement, npcId);
								if(type == RewardType.SWEEP || type == RewardType.NOT_RATED_NOT_GROUPED)
									warn("Can't load rewardlist from group: " + npcId + "; type: " + type);
								else if(data != null)
									group.addData(data);
							}

							if(group != null && group.getItems().size() > 0)
								list.add(group);
						}
						else if(nextName.equalsIgnoreCase("reward"))
						{
							if(type != RewardType.SWEEP && type != RewardType.NOT_RATED_NOT_GROUPED)
							{
								warn("Reward can't be without group(and not grouped): " + npcId + "; type: " + type);
								continue;
							}

							RewardData data = parseReward(nextElement, npcId);
							RewardGroup g = new RewardGroup(RewardList.MAX_CHANCE);
							g.addData(data);
							list.add(g);
						}
					}

					if(type == RewardType.RATED_GROUPED || type == RewardType.NOT_RATED_GROUPED)
						if(!list.validate())
							warn("Problems with rewardlist for npc: " + npcId + "; type: " + type);

					template.putRewardList(type, list);
				}
				else if(nodeName.equalsIgnoreCase("skills"))
				{
					for(Iterator<org.dom4j.Element> nextIterator = secondElement.elementIterator(); nextIterator.hasNext();)
					{
						org.dom4j.Element nextElement = nextIterator.next();
						int id = Integer.parseInt(nextElement.attributeValue("id"));
						int level = Integer.parseInt(nextElement.attributeValue("level"));

						// Для определения расы используется скилл 4416
						if(id == 4416)
						{
							template.setRace(level);
						}

						Skill skill = SkillTable.getInstance().getInfo(id, level);

						//TODO
						//if(skill == null || skill.getSkillType() == L2Skill.SkillType.NOTDONE)
						//	unimpl.add(Integer.valueOf(skillId));
						if(skill == null)
						{
							continue;
						}

						template.addSkill(skill);
					}
				}
				else if(nodeName.equalsIgnoreCase("minions"))
				{
					for(Iterator<org.dom4j.Element> nextIterator = secondElement.elementIterator(); nextIterator.hasNext();)
					{
						org.dom4j.Element nextElement = nextIterator.next();
						int id = Integer.parseInt(nextElement.attributeValue("npc_id"));
						int count = Integer.parseInt(nextElement.attributeValue("count"));

						template.addMinion(new MinionData(id, count));
					}
				}
				else if(nodeName.equalsIgnoreCase("teach_classes"))
				{
					for(Iterator<org.dom4j.Element> nextIterator = secondElement.elementIterator(); nextIterator.hasNext();)
					{
						org.dom4j.Element nextElement = nextIterator.next();

						int id = Integer.parseInt(nextElement.attributeValue("id"));

						template.addTeachInfo(ClassId.VALUES[id]);
					}
				}
				else if(nodeName.equalsIgnoreCase("absorblist"))
				{
					for(Iterator<org.dom4j.Element> nextIterator = secondElement.elementIterator(); nextIterator.hasNext();)
					{
						org.dom4j.Element nextElement = nextIterator.next();

						int chance = Integer.parseInt(nextElement.attributeValue("chance"));
						int cursedChance = nextElement.attributeValue("cursed_chance") == null ? 0 : Integer.parseInt(nextElement.attributeValue("cursed_chance"));
						int minLevel = Integer.parseInt(nextElement.attributeValue("min_level"));
						int maxLevel = Integer.parseInt(nextElement.attributeValue("max_level"));
						boolean skill = nextElement.attributeValue("skill") != null && Boolean.parseBoolean(nextElement.attributeValue("skill"));
						AbsorbInfo.AbsorbType absorbType = AbsorbInfo.AbsorbType.valueOf(nextElement.attributeValue("type"));

						template.addAbsorbInfo(new AbsorbInfo(skill, absorbType, chance, cursedChance, minLevel, maxLevel));
					}
				}
				else if(nodeName.equalsIgnoreCase("teleportlist"))
				{
					for(Iterator<org.dom4j.Element> sublistIterator = secondElement.elementIterator(); sublistIterator.hasNext();)
					{
						org.dom4j.Element subListElement = sublistIterator.next();
						int id = Integer.parseInt(subListElement.attributeValue("id"));
						List<TeleportLocation> list = new ArrayList<TeleportLocation>();
						for(Iterator<org.dom4j.Element> targetIterator = subListElement.elementIterator(); targetIterator.hasNext();)
						{
							org.dom4j.Element targetElement = targetIterator.next();
							int itemId = Integer.parseInt(targetElement.attributeValue("item_id", "57"));
							long price = Integer.parseInt(targetElement.attributeValue("price"));
							int npcStringId = Integer.parseInt(targetElement.attributeValue("name"));
							int castleId = Integer.parseInt(targetElement.attributeValue("castle_id", "0"));
							TeleportLocation loc = new TeleportLocation(itemId, price, npcStringId, castleId);
							loc.set(Location.parseLoc(targetElement.attributeValue("loc")));
							list.add(loc);
						}
						template.addTeleportList(id, list.toArray(new TeleportLocation[list.size()]));
					}
				}
			}

			getHolder().addTemplate(template);
		}
	}

	private void multStats(StatsSet set) 
	{
		double pAtk = set.getDouble("basePAtk", 0);
		double mAtk = set.getDouble("baseMAtk", 0);
		double pDef = set.getDouble("basePDef", 0);
		double mDef = set.getDouble("baseMDef" , 0);
		String type = set.getString("type");
		if(type.equals("Monster"))
		{
			pAtk *= Config.MONSTER_ATK_MULT;
			mAtk *= Config.MONSTER_ATK_MULT;
			pDef *= Config.MONSTER_DEF_MULT;
			mDef *= Config.MONSTER_DEF_MULT;
		}
		else if(type.equals("RaidBoss"))
		{
			pAtk *= Config.RAID_ATK_MULT;
			mAtk *= Config.RAID_ATK_MULT;
			pDef *= Config.RAID_DEF_MULT;
			mDef *= Config.RAID_DEF_MULT;
		}
		else if(type.equals("Boss") || type.equals("QueenAnt") || type.equals("Orfen") || type.equals("ReflectionBoss"))
		{
			pAtk *= Config.EPIC_ATK_MULT;
			mAtk *= Config.EPIC_ATK_MULT;
			pDef *= Config.EPIC_DEF_MULT;
			mDef *= Config.EPIC_DEF_MULT;
		}
		
		if(pAtk != 0)
			set.set("basePAtk", pAtk);
		if(mAtk != 0)
			set.set("baseMAtk", mAtk);
		if(pDef != 0)
			set.set("basePDef", pDef);
		if(mDef != 0)
			set.set("baseMDef", mDef);
	}

	private static final int[] LOWERED_CHANCE_ITEMS = {8606, 8608, 8610, 10655, 10656, 8607, 8609, 8612, 8613, 8611, 10657};
	private RewardData parseReward(org.dom4j.Element rewardElement, int mobLevel)
	{
		boolean loweredItem = false;
		int itemId = Integer.parseInt(rewardElement.attributeValue("item_id"));
		for(int id : LOWERED_CHANCE_ITEMS)
			if(itemId == id)
				loweredItem = true;
		
		int min = Integer.parseInt(rewardElement.attributeValue("min"));
		int max = Integer.parseInt(rewardElement.attributeValue("max"));
		// переводим в системный вид
		int chance = (int)(Double.parseDouble(rewardElement.attributeValue("chance")) * 10000);
		
		if(loweredItem)
			chance /= 10;
		if(itemId == 13028)//Vitality
			if(mobLevel < 65)
				chance *= 6;
		RewardData data = new RewardData(itemId);
		if(data.getItem().isCommonItem())
		{
			data.setChance(chance * Config.RATE_DROP_COMMON_ITEMS);
		}
		else
		{
			data.setChance(chance);
		}

		data.setMinDrop(min);
		data.setMaxDrop(max);

		return data;
	}
}
