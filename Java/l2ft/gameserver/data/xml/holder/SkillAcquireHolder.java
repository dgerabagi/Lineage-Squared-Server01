//
// C:\l2sq\Pac Project\Java\l2ft\gameserver\data\xml\holder\SkillAcquireHolder.java
//
package l2ft.gameserver.data.xml.holder;

import gnu.trove.TIntObjectHashMap;
import gnu.trove.TIntObjectIterator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import l2ft.commons.data.xml.AbstractHolder;
import l2ft.gameserver.model.Player;
import l2ft.gameserver.model.Skill;
import l2ft.gameserver.model.SkillLearn;
import l2ft.gameserver.model.base.AcquireType;
import l2ft.gameserver.model.base.ClassId;
import l2ft.gameserver.model.pledge.Clan;
import l2ft.gameserver.model.pledge.SubUnit;

public final class SkillAcquireHolder extends AbstractHolder {
	private static final SkillAcquireHolder _instance = new SkillAcquireHolder();

	public static SkillAcquireHolder getInstance() {
		return _instance;
	}

	// Class-based skill trees
	private TIntObjectHashMap<List<SkillLearn>> _normalSkillTree = new TIntObjectHashMap<List<SkillLearn>>();
	private TIntObjectHashMap<List<SkillLearn>> _transferSkillTree = new TIntObjectHashMap<List<SkillLearn>>();

	// Race-based skill trees
	private TIntObjectHashMap<List<SkillLearn>> _fishingSkillTree = new TIntObjectHashMap<List<SkillLearn>>();
	private TIntObjectHashMap<List<SkillLearn>> _transformationSkillTree = new TIntObjectHashMap<List<SkillLearn>>();

	// No-dependency skill lists
	private List<SkillLearn> _certificationSkillTree = new ArrayList<SkillLearn>();
	private List<SkillLearn> _collectionSkillTree = new ArrayList<SkillLearn>();
	private List<SkillLearn> _pledgeSkillTree = new ArrayList<SkillLearn>();
	private List<SkillLearn> _subUnitSkillTree = new ArrayList<SkillLearn>();

	public int getMinLevelForNewSkill(Player player, AcquireType type) {
		List<SkillLearn> skills;
		switch (type) {
			case NORMAL:
				skills = _normalSkillTree.get(player.getActiveClassClassId().getId());
				if (skills == null) {
					info("skill tree for class " + player.getActiveClassClassId().getId() + " is not defined !");
					return 0;
				}
				break;
			case TRANSFORMATION:
				skills = _transformationSkillTree.get(player.getRace().ordinal());
				if (skills == null) {
					info("skill tree for race " + player.getRace().ordinal() + " is not defined !");
					return 0;
				}
				break;
			case FISHING:
				skills = _fishingSkillTree.get(player.getRace().ordinal());
				if (skills == null) {
					info("skill tree for race " + player.getRace().ordinal() + " is not defined !");
					return 0;
				}
				break;
			default:
				return 0;
		}
		int minlevel = 0;
		for (SkillLearn temp : skills) {
			if (temp.getMinLevel() > player.getLevel()) {
				if (minlevel == 0 || temp.getMinLevel() < minlevel) {
					minlevel = temp.getMinLevel();
				}
			}
		}
		return minlevel;
	}

	public Collection<SkillLearn> getAvailableSkills(Player player, AcquireType type) {
		return getAvailableSkills(player, type, null);
	}

	private List<SkillLearn> getMainNormalSkillTree(Player player) {
		// We gather normal skill tree from primary + secondary class
		List<SkillLearn> skills = new ArrayList<SkillLearn>();
		if (player.getActiveClassClassId().level() <= 1) {
			return skills;
		}
		List<SkillLearn> newSkills = _normalSkillTree.get(player.getActiveClass().getFirstClassId());
		// Add any that might be available for main
		skills.addAll(getAvaliableList(newSkills, player.getAllSkillsArray(), player.getLevel()));

		if (ClassId.values()[player.getSecondaryClassId()].getLevel() <= 1) {
			return skills;
		}
		newSkills = _normalSkillTree.get(player.getSecondaryClassId());
		int secondaryLevel = player.getActiveClass().getSecondaryLevel();
		if (secondaryLevel > player.getLevel()) {
			secondaryLevel = player.getLevel();
		}

		Collection<SkillLearn> secondarySkills = getAvaliableList(newSkills, player.getAllSkillsArray(),
				secondaryLevel);

		// Merge them so as not to double-add
		for (SkillLearn learn : secondarySkills) {
			boolean foundAlready = false;
			for (SkillLearn oldLearn : skills) {
				if (learn.getId() == oldLearn.getId() && learn.getLevel() <= oldLearn.getLevel()) {
					foundAlready = true;
					break;
				}
			}
			if (!foundAlready) {
				skills.add(learn);
			}
		}
		return skills;
	}

	public Collection<SkillLearn> getAvailableSkills(Player player, AcquireType type, SubUnit subUnit) {
		Collection<SkillLearn> skills;
		switch (type) {
			case NORMAL:
				return getMainNormalSkillTree(player);

			case COLLECTION:
				skills = _collectionSkillTree;
				if (skills == null) {
					info("skill tree for class " + player.getActiveClassClassId().getId() + " is not defined !");
					return Collections.<SkillLearn>emptyList();
				}
				return getAvaliableList(skills, player.getAllSkillsArray(), player.getLevel());

			case TRANSFORMATION:
				skills = _transformationSkillTree.get(player.getRace().ordinal());
				if (skills == null) {
					info("skill tree for race " + player.getRace().ordinal() + " is not defined !");
					return Collections.<SkillLearn>emptyList();
				}
				return getAvaliableList(skills, player.getAllSkillsArray(), player.getLevel());

			case TRANSFER_EVA_SAINTS:
			case TRANSFER_SHILLIEN_SAINTS:
			case TRANSFER_CARDINAL: {
				skills = _transferSkillTree.get(type.transferClassId());
				if (skills == null) {
					info("skill tree for class " + type.transferClassId() + " is not defined !");
					return Collections.<SkillLearn>emptyList();
				}
				if (player == null) {
					return skills;
				}
				Map<Integer, SkillLearn> skillLearnMap = new TreeMap<Integer, SkillLearn>();
				for (SkillLearn temp : skills) {
					if (temp.getMinLevel() <= player.getLevel()) {
						int knownLevel = player.getSkillLevel(temp.getId());
						if (knownLevel == -1) {
							skillLearnMap.put(temp.getId(), temp);
						}
					}
				}
				return skillLearnMap.values();
			}
			case FISHING:
				skills = _fishingSkillTree.get(player.getRace().ordinal());
				if (skills == null) {
					info("skill tree for race " + player.getRace().ordinal() + " is not defined !");
					return Collections.<SkillLearn>emptyList();
				}
				return getAvaliableList(skills, player.getAllSkillsArray(), player.getLevel());

			case CLAN:
				skills = _pledgeSkillTree;
				Collection<Skill> skls = player.getClan().getSkills();
				return getAvaliableList(skills, skls.toArray(new Skill[skls.size()]), player.getClan().getLevel());

			case SUB_UNIT:
				skills = _subUnitSkillTree;
				Collection<Skill> st = subUnit.getSkills();
				return getAvaliableList(skills, st.toArray(new Skill[st.size()]), player.getClan().getLevel());

			case CERTIFICATION:
				skills = _certificationSkillTree;
				if (player == null) {
					return skills;
				} else {
					return getAvaliableList(skills, player.getAllSkillsArray(), player.getLevel());
				}

			default:
				return Collections.<SkillLearn>emptyList();
		}
	}

	private static Collection<SkillLearn> getAvaliableList(Collection<SkillLearn> skillLearns, Skill[] skills,
			int level) {
		if (skillLearns == null) {
			return Collections.<SkillLearn>emptyList();
		}
		Map<Integer, SkillLearn> skillLearnMap = new TreeMap<Integer, SkillLearn>();
		for (SkillLearn temp : skillLearns) {
			if (temp.getMinLevel() <= level) {
				boolean knownSkill = false;
				for (int j = 0; j < skills.length && !knownSkill; j++) {
					if (skills[j].getId() == temp.getId()) {
						knownSkill = true;
						if (skills[j].getLevel() == temp.getLevel() - 1) {
							skillLearnMap.put(temp.getId(), temp);
						}
					}
				}
				if (!knownSkill && temp.getLevel() == 1) {
					skillLearnMap.put(temp.getId(), temp);
				}
			}
		}
		return skillLearnMap.values();
	}

	public boolean isItClassSkill(int level, int classId, Skill skill) {
		return isSkillPossible(level, _normalSkillTree.get(classId), skill);
	}

	public boolean isItPomanderClassSkill(int level, int classId, Skill skill) {
		AcquireType type = AcquireType.transferType(classId);
		if (type == null) {
			return false;
		}
		return isSkillPossible(level, _transferSkillTree.get(type.transferClassId()), skill);
	}

	public SkillLearn getSkillLearn(Player player, int id, int level, AcquireType type) {
		List<SkillLearn> skills;
		switch (type) {
			case NORMAL:
				skills = getMainNormalSkillTree(player);
				break;
			case COLLECTION:
				skills = _collectionSkillTree;
				break;
			case TRANSFORMATION:
				skills = _transformationSkillTree.get(player.getRace().ordinal());
				break;
			case TRANSFER_CARDINAL:
			case TRANSFER_SHILLIEN_SAINTS:
			case TRANSFER_EVA_SAINTS:
				skills = _transferSkillTree.get(player.getActiveClassClassId().getId());
				break;
			case FISHING:
				skills = _fishingSkillTree.get(player.getRace().ordinal());
				break;
			case CLAN:
				skills = _pledgeSkillTree;
				break;
			case SUB_UNIT:
				skills = _subUnitSkillTree;
				break;
			case CERTIFICATION:
				skills = _certificationSkillTree;
				break;
			default:
				return null;
		}

		if (skills == null) {
			return null;
		}

		for (SkillLearn temp : skills) {
			if (temp.getLevel() == level && temp.getId() == id) {
				return temp;
			}
		}
		return null;
	}

	public SkillLearn giveSkillLearnWithoutQuestions(AcquireType type, Player player, int id, int level) {
		switch (type) {
			case NORMAL: {
				List<SkillLearn> skills = new ArrayList<SkillLearn>();
				if (player.getSecondaryClassId() != 0) {
					skills.addAll(_normalSkillTree.get(player.getSecondaryClassId()));
				}
				skills.addAll(_normalSkillTree.get(player.getActiveClassClassId().getId()));

				SkillLearn bestLearn = null;
				int learnCost = Integer.MAX_VALUE;
				char learnLevel = 100;

				for (SkillLearn temp : skills) {
					if (temp.getLevel() == level && temp.getId() == id) {
						if (temp.getMinLevel() < learnLevel || temp.getCost() < learnCost) {
							bestLearn = temp;
							learnCost = temp.getCost();
							learnLevel = (char) temp.getMinLevel();
						}
					}
				}
				return bestLearn;
			}
			default:
				return getSkillLearn(player, id, level, type);
		}
	}

	public boolean isSkillPossible(Player player, Skill skill, AcquireType type) {
		Clan clan = null;
		List<SkillLearn> skills;
		switch (type) {
			case NORMAL: {
				int secondaryLevel = player.getActiveClass().getSecondaryLevel();
				if (secondaryLevel > player.getLevel()) {
					secondaryLevel = player.getLevel();
				}
				if (!isSkillPossible(player.getLevel(), _normalSkillTree.get(player.getActiveClass().getFirstClassId()),
						skill)) {
					if (!isSkillPossible(secondaryLevel, _normalSkillTree.get(player.getSecondaryClassId()), skill)) {
						return false;
					}
				}
				return true;
			}
			case COLLECTION:
				skills = _collectionSkillTree;
				break;
			case TRANSFORMATION:
				skills = _transformationSkillTree.get(player.getRace().ordinal());
				break;
			case FISHING:
				skills = _fishingSkillTree.get(player.getRace().ordinal());
				break;
			case TRANSFER_CARDINAL:
			case TRANSFER_EVA_SAINTS:
			case TRANSFER_SHILLIEN_SAINTS: {
				int transferId = type.transferClassId();
				if (player.getActiveClassClassId().getId() == transferId
						|| player.getSecondaryClassId() == transferId) {
					skills = _transferSkillTree.get(transferId);
				} else {
					return false;
				}
				break;
			}
			case CLAN:
				clan = player.getClan();
				if (clan == null) {
					return false;
				}
				skills = _pledgeSkillTree;
				break;
			case SUB_UNIT:
				clan = player.getClan();
				if (clan == null) {
					return false;
				}
				skills = _subUnitSkillTree;
				break;
			case CERTIFICATION:
				skills = _certificationSkillTree;
				break;
			default:
				return false;
		}
		return isSkillPossible(player.getLevel(), skills, skill);
	}

	private boolean isSkillPossible(int level, Collection<SkillLearn> skills, Skill skill) {
		if (skills == null || skills.isEmpty()) {
			return false;
		}
		int skillLevel = skill.getLevel();
		if (skillLevel > skill.getBaseLevel()) {
			skillLevel = skill.getBaseLevel();
		}
		for (SkillLearn learn : skills) {
			if (learn.getId() == skill.getId()
					&& learn.getLevel() == skillLevel
					&& learn.getMinLevel() <= level) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Returns the normal skill tree (as a Collection<SkillLearn>) for a specific
	 * classId,
	 * or an empty List if none found.
	 */
	public Collection<SkillLearn> getNormalSkillTreeForClassId(int classId) {
		List<SkillLearn> skills = _normalSkillTree.get(classId);
		return (skills != null) ? skills : Collections.<SkillLearn>emptyList();
	}

	/**
	 * Checks if 'skill' is valid for the given 'classId' and forcibly-lowered
	 * 'level'.
	 * Does a direct check against the normal skill tree for one class.
	 */
	public boolean isSkillPossibleAtLevel(int level, int classId, Skill skill) {
		List<SkillLearn> skillLearns = _normalSkillTree.get(classId);
		if (skillLearns == null || skillLearns.isEmpty()) {
			return false;
		}
		// Reuse the existing approach:
		return isSkillPossible(level, skillLearns, skill);
	}

	public List<Skill> getNotAllowedSkills(Player player, int classToRemove, Collection<Skill> allSkillsList) {
		List<Skill> skillsToRemove = new ArrayList<Skill>();
		List<SkillLearn> badSkillLearns = new ArrayList<SkillLearn>();

		badSkillLearns.addAll(_normalSkillTree.get(classToRemove));
		if (classToRemove == ClassId.cardinal.getId()
				|| classToRemove == ClassId.evaSaint.getId()
				|| classToRemove == ClassId.shillienSaint.getId()) {
			badSkillLearns.addAll(_transferSkillTree.get(classToRemove));
		}

		for (SkillLearn learn : badSkillLearns) {
			for (Skill existingSkill : allSkillsList) {
				if (existingSkill.getId() == learn.getId()) {
					skillsToRemove.add(existingSkill);
				}
			}
		}
		return skillsToRemove;
	}

	public boolean isSkillPossible(Player player, Skill skill) {
		for (AcquireType aq : AcquireType.VALUES) {
			if (isSkillPossible(player, skill, aq)) {
				return true;
			}
		}
		return false;
	}

	public Collection<SkillLearn> getAllSkillsForClass(int classId, Skill[] learnedSkills, int level) {
		Collection<SkillLearn> skills = _normalSkillTree.get(classId);
		return getAvaliableList(skills, learnedSkills, level);
	}

	public List<SkillLearn> getSkillLearnListByItemId(Player player, int itemId) {
		List<SkillLearn> learns = new ArrayList<SkillLearn>();
		learns.addAll(_normalSkillTree.get(player.getActiveClassClassId().getId()));
		learns.addAll(_normalSkillTree.get(player.getSecondaryClassId()));

		List<SkillLearn> result = new ArrayList<SkillLearn>(1);
		for (SkillLearn sl : learns) {
			if (sl.getItemId() == itemId) {
				result.add(sl);
			}
		}
		return result;
	}

	public List<SkillLearn> getAllNormalSkillTreeWithForgottenScrolls() {
		List<SkillLearn> a = new ArrayList<SkillLearn>();
		for (TIntObjectIterator<List<SkillLearn>> i = _normalSkillTree.iterator(); i.hasNext();) {
			i.advance();
			for (SkillLearn learn : i.value()) {
				if (learn.getItemId() > 0 && learn.isClicked()) {
					a.add(learn);
				}
			}
		}
		return a;
	}

	public void addAllNormalSkillLearns(TIntObjectHashMap<List<SkillLearn>> map) {
		// In normal skill tree, we add the base + inherited from parent classes
		for (ClassId cId : ClassId.VALUES) {
			if (cId.name().startsWith("dummyEntry")) {
				continue;
			}
			int classID = cId.getId();
			List<SkillLearn> temp = map.get(classID);
			if (temp == null) {
				info("Not found NORMAL skill learn for class " + classID);
				continue;
			}
			_normalSkillTree.put(classID, temp);

			// Climb up parents
			ClassId father = cId.getParent(0);
			ClassId secondparent = cId.getParent(1);
			if (secondparent == father) {
				secondparent = null;
			}
			while (father != null || secondparent != null) {
				if (father != null) {
					List<SkillLearn> pList = _normalSkillTree.get(father.getId());
					if (pList != null) {
						temp.addAll(pList);
					}
					father = father.getParent(0);
				} else if (secondparent != null) {
					List<SkillLearn> pList = _normalSkillTree.get(secondparent.getId());
					if (pList != null) {
						temp.addAll(pList);
					}
					secondparent = secondparent.getParent(1);
				}
			}
		}
	}

	public void addAllFishingLearns(int race, List<SkillLearn> s) {
		_fishingSkillTree.put(race, s);
	}

	public void addAllTransferLearns(int classId, List<SkillLearn> s) {
		_transferSkillTree.put(classId, s);
	}

	public void addAllTransformationLearns(int race, List<SkillLearn> s) {
		_transformationSkillTree.put(race, s);
	}

	public void addAllCollectionLearns(List<SkillLearn> s) {
		_collectionSkillTree.addAll(s);
	}

	public void addAllSubUnitLearns(List<SkillLearn> s) {
		_subUnitSkillTree.addAll(s);
	}

	public void addAllPledgeLearns(List<SkillLearn> s) {
		_pledgeSkillTree.addAll(s);
	}

	public void addAllCertificationLearns(List<SkillLearn> s) {
		_certificationSkillTree.addAll(s);
	}

	@Override
	public void log() {
		info("load " + sizeTroveMap(_normalSkillTree) + " normal learns for " + _normalSkillTree.size() + " classes.");
		info("load " + sizeTroveMap(_transferSkillTree) + " transfer learns for " + _transferSkillTree.size()
				+ " classes.");
		info("load " + sizeTroveMap(_transformationSkillTree) + " transformation learns for "
				+ _transformationSkillTree.size() + " races.");
		info("load " + sizeTroveMap(_fishingSkillTree) + " fishing learns for " + _fishingSkillTree.size() + " races.");
		info("load " + _collectionSkillTree.size() + " collection learns.");
		info("load " + _pledgeSkillTree.size() + " pledge learns.");
		info("load " + _subUnitSkillTree.size() + " sub unit learns.");
	}

	@Deprecated
	@Override
	public int size() {
		return 0;
	}

	@Override
	public void clear() {
		_normalSkillTree.clear();
		_fishingSkillTree.clear();
		_transferSkillTree.clear();
		_certificationSkillTree.clear();
		_collectionSkillTree.clear();
		_pledgeSkillTree.clear();
		_subUnitSkillTree.clear();
	}

	private static int sizeTroveMap(TIntObjectHashMap<List<SkillLearn>> a) {
		int i = 0;
		for (TIntObjectIterator<List<SkillLearn>> iterator = a.iterator(); iterator.hasNext();) {
			iterator.advance();
			i += iterator.value().size();
		}
		return i;
	}
}
