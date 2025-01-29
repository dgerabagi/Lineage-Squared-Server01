package l2ft.gameserver.model.base;

import static l2ft.gameserver.model.base.ClassLevel.First;
import static l2ft.gameserver.model.base.ClassLevel.Fourth;
import static l2ft.gameserver.model.base.ClassLevel.Second;
import static l2ft.gameserver.model.base.ClassLevel.Third;
import static l2ft.gameserver.model.base.ClassType.Fighter;
import static l2ft.gameserver.model.base.ClassType.Mystic;
import static l2ft.gameserver.model.base.ClassType.Priest;
import static l2ft.gameserver.model.base.Race.darkelf;
import static l2ft.gameserver.model.base.Race.dwarf;
import static l2ft.gameserver.model.base.Race.elf;
import static l2ft.gameserver.model.base.Race.human;
import static l2ft.gameserver.model.base.Race.kamael;
import static l2ft.gameserver.model.base.Race.orc;

import java.util.EnumSet;

public enum PlayerClass
{
	HumanFighter(human, Fighter, First), //0
	Warrior(human, Fighter, Second), //1
	Gladiator(human, Fighter, Third), //2
	Warlord(human, Fighter, Third), //3
	HumanKnight(human, Fighter, Second), //4
	Paladin(human, Fighter, Third), //5
	DarkAvenger(human, Fighter, Third), //6
	Rogue(human, Fighter, Second), //7
	TreasureHunter(human, Fighter, Third), //8
	Hawkeye(human, Fighter, Third), //9
	HumanMystic(human, Mystic, First), //10
	HumanWizard(human, Mystic, Second), //11
	Sorceror(human, Mystic, Third), //12
	Necromancer(human, Mystic, Third), //13
	Warlock(human, Mystic, Third), //14
	Cleric(human, Priest, Second), //15
	Bishop(human, Priest, Third), //16
	Prophet(human, Priest, Third), //17

	ElvenFighter(elf, Fighter, First), //18
	ElvenKnight(elf, Fighter, Second), //19
	TempleKnight(elf, Fighter, Third), //20
	Swordsinger(elf, Fighter, Third), //21
	ElvenScout(elf, Fighter, Second), //22
	Plainswalker(elf, Fighter, Third), //23
	SilverRanger(elf, Fighter, Third), //24
	ElvenMystic(elf, Mystic, First), //25
	ElvenWizard(elf, Mystic, Second), //26
	Spellsinger(elf, Mystic, Third), //27
	ElementalSummoner(elf, Mystic, Third), //28
	ElvenOracle(elf, Priest, Second), //29
	ElvenElder(elf, Priest, Third), //30

	DarkElvenFighter(darkelf, Fighter, First), //31
	PalusKnight(darkelf, Fighter, Second), //32
	ShillienKnight(darkelf, Fighter, Third), //33
	Bladedancer(darkelf, Fighter, Third), //34
	Assassin(darkelf, Fighter, Second), //35
	AbyssWalker(darkelf, Fighter, Third), //36
	PhantomRanger(darkelf, Fighter, Third), //37
	DarkElvenMystic(darkelf, Mystic, First), //38
	DarkElvenWizard(darkelf, Mystic, Second), //39
	Spellhowler(darkelf, Mystic, Third), //40
	PhantomSummoner(darkelf, Mystic, Third), //41
	ShillienOracle(darkelf, Priest, Second), //42
	ShillienElder(darkelf, Priest, Third), //43

	OrcFighter(orc, Fighter, First), //44
	orcRaider(orc, Fighter, Second), //45
	Destroyer(orc, Fighter, Third), //46
	orcMonk(orc, Fighter, Second), //47
	Tyrant(orc, Fighter, Third), //48
	orcMystic(orc, Mystic, First), //49
	orcShaman(orc, Mystic, Second), //50
	Overlord(orc, Mystic, Third), //51
	Warcryer(orc, Mystic, Third), //52

	DwarvenFighter(dwarf, Fighter, First), //53
	DwarvenScavenger(dwarf, Fighter, Second), //54
	BountyHunter(dwarf, Fighter, Third), //55
	DwarvenArtisan(dwarf, Fighter, Second), //56
	Warsmith(dwarf, Fighter, Third), //57

	DummyEntry1(null, null, null), //58
	DummyEntry2(null, null, null), //59
	DummyEntry3(null, null, null), //60
	DummyEntry4(null, null, null), //61
	DummyEntry5(null, null, null), //62
	DummyEntry6(null, null, null), //63
	DummyEntry7(null, null, null), //64
	DummyEntry8(null, null, null), //65
	DummyEntry9(null, null, null), //66
	DummyEntry10(null, null, null), //67
	DummyEntry11(null, null, null), //68
	DummyEntry12(null, null, null), //69
	DummyEntry13(null, null, null), //70
	DummyEntry14(null, null, null), //71
	DummyEntry15(null, null, null), //72
	DummyEntry16(null, null, null), //73
	DummyEntry17(null, null, null), //74
	DummyEntry18(null, null, null), //75
	DummyEntry19(null, null, null), //76
	DummyEntry20(null, null, null), //77
	DummyEntry21(null, null, null), //78
	DummyEntry22(null, null, null), //79
	DummyEntry23(null, null, null), //80
	DummyEntry24(null, null, null), //81
	DummyEntry25(null, null, null), //82
	DummyEntry26(null, null, null), //83
	DummyEntry27(null, null, null), //84
	DummyEntry28(null, null, null), //85
	DummyEntry29(null, null, null), //86
	DummyEntry30(null, null, null), //87

	Duelist(human, Fighter, Fourth), //88
	Dreadnought(human, Fighter, Fourth), //89
	PhoenixKnight(human, Fighter, Fourth), //90
	HellKnight(human, Fighter, Fourth), //91
	Sagittarius(human, Fighter, Fourth), //92
	Adventurer(human, Fighter, Fourth), //93
	Archmage(human, Mystic, Fourth), //94
	Soultaker(human, Mystic, Fourth), //95
	ArcanaLord(human, Mystic, Fourth), //96
	Cardinal(human, Priest, Fourth), //97
	Hierophant(human, Priest, Fourth), //98

	EvaTemplar(elf, Fighter, Fourth), //99
	SwordMuse(elf, Fighter, Fourth), //100
	WindRider(elf, Fighter, Fourth), //101
	MoonlightSentinel(elf, Fighter, Fourth), //102
	MysticMuse(elf, Mystic, Fourth), //103
	ElementalMaster(elf, Mystic, Fourth), //104
	EvaSaint(elf, Priest, Fourth), //105

	ShillienTemplar(darkelf, Fighter, Fourth), //106
	SpectralDancer(darkelf, Fighter, Fourth), //107
	GhostHunter(darkelf, Fighter, Fourth), //108
	GhostSentinel(darkelf, Fighter, Fourth), //109
	StormScreamer(darkelf, Mystic, Fourth), //110
	SpectralMaster(darkelf, Mystic, Fourth), //111
	ShillienSaint(darkelf, Priest, Fourth), //112

	Titan(orc, Fighter, Fourth), //113
	GrandKhauatari(orc, Fighter, Fourth), //114
	Dominator(orc, Mystic, Fourth), //115
	Doomcryer(orc, Mystic, Fourth), //116

	FortuneSeeker(dwarf, Fighter, Fourth), //117
	Maestro(dwarf, Fighter, Fourth), //118

	DummyEntry31(null, null, null), //119
	DummyEntry32(null, null, null), //120
	DummyEntry33(null, null, null), //121
	DummyEntry34(null, null, null), //122

	/** Kamael */
	MaleSoldier(kamael, Fighter, First), // 123
	FemaleSoldier(kamael, Fighter, First), //124
	Troopier(kamael, Fighter, Second), // 125
	Warder(kamael, Fighter, Second), //126
	Berserker(kamael, Fighter, Third), //127
	MaleSoulbreaker(kamael, Fighter, Third), //128
	FemaleSoulbreaker(kamael, Fighter, Third), //129
	Arbalester(kamael, Fighter, Third), //130

	/** kamael */
	Doombringer(kamael, Fighter, Fourth), //131
	MaleSoulHound(kamael, Fighter, Fourth), //132
	FemaleSoulHound(kamael, Fighter, Fourth), //133
	Trickster(kamael, Fighter, Fourth), //134
	Inspector(kamael, Fighter, Third), //135
	Judicator(kamael, Fighter, Fourth); //136

	public static final PlayerClass[] VALUES = values();

	private Race _race;
	private ClassLevel _level;
	private ClassType _type;

	PlayerClass(Race race, ClassType type, ClassLevel level)
	{
		_race = race;
		_level = level;
		_type = type;
	}

	public static EnumSet<PlayerClass> getSet(Race race, ClassLevel level)
	{
		EnumSet<PlayerClass> allOf = EnumSet.noneOf(PlayerClass.class);

		for(PlayerClass playerClass : EnumSet.allOf(PlayerClass.class))
			if(race == null || playerClass.isOfRace(race))
				if(level == null || playerClass.isOfLevel(level))
					allOf.add(playerClass);

		return allOf;
	}

	public final boolean isOfRace(Race race)
	{
		return _race == race;
	}

	public final boolean isOfType(ClassType type)
	{
		return _type == type;
	}

	public final boolean isOfLevel(ClassLevel level)
	{
		return _level == level;
	}

	/**
	 * ĐźŃ€ĐľĐ˛ĐµŃ€ŃŹĐµŃ‚ ĐżŃ€Đ¸Đ˝Ń†Đ¸ĐżĐ¸Đ°Đ»ŃŚĐ˝Ń�ŃŽ Ń�ĐľĐ˛ĐĽĐµŃ�Ń‚Đ¸ĐĽĐľŃ�Ń‚ŃŚ Đ´Đ˛Ń�Ń… Ń�Đ°Đ±ĐľĐ˛.
	 */
	/**public static boolean areClassesComportable(PlayerClass c1, PlayerClass c2)
	{
		if(c1.isOfRace(Race.kamael) != c2.isOfRace(Race.kamael))
			return false; // ĐşĐ°ĐĽĐ°ŃŤĐ»Đ¸ Ń‚ĐľĐ»ŃŚĐşĐľ Ń� ĐşĐ°ĐĽĐ°ŃŤĐ»ŃŹĐĽĐ¸
		if(c1.isOfRace(Race.elf) && c2.isOfRace(Race.darkelf) || c1.isOfRace(Race.darkelf) && c2.isOfRace(Race.elf))
			return false; // ŃŤĐ»ŃŚŃ„Ń‹ Đ˝ĐµŃ�ĐľĐ˛ĐĽĐµŃ�Ń‚Đ¸ĐĽŃ‹ Ń� Ń‚ĐµĐĽĐ˝Ń‹ĐĽĐ¸
		if(c1 == PlayerClass.Overlord || c1 == PlayerClass.Warsmith || c2 == PlayerClass.Overlord || c2 == PlayerClass.Warsmith)
			return false; // ŃŤŃ‚Đ¸ Đ˛ĐľĐľĐ±Ń‰Đµ
		if(subclassSetMap.get(c1) == subclassSetMap.get(c2))
			return false; // ĐľĐ´Đ˝ĐľŃ‚Đ¸ĐżĐ˝Ń‹Đµ
		return true;
	}*/
}