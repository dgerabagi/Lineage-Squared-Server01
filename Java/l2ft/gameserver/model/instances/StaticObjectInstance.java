package l2ft.gameserver.model.instances;

import java.util.Collections;
import java.util.List;

import l2ft.commons.lang.reference.HardReference;
import l2ft.gameserver.ai.CtrlIntention;
import l2ft.gameserver.model.Creature;
import l2ft.gameserver.model.GameObject;
import l2ft.gameserver.model.Player;
import l2ft.gameserver.model.World;
import l2ft.gameserver.model.reference.L2Reference;
import l2ft.gameserver.network.l2.s2c.L2GameServerPacket;
import l2ft.gameserver.network.l2.s2c.MyTargetSelected;
import l2ft.gameserver.network.l2.s2c.NpcHtmlMessage;
import l2ft.gameserver.network.l2.s2c.ShowTownMap;
import l2ft.gameserver.network.l2.s2c.StaticObject;
import l2ft.gameserver.scripts.Events;
import l2ft.gameserver.templates.StaticObjectTemplate;
import l2ft.gameserver.utils.Location;


public class StaticObjectInstance extends GameObject
{
	private final HardReference<StaticObjectInstance> reference;
	private final StaticObjectTemplate _template;
	private int _meshIndex;

	public StaticObjectInstance(int objectId, StaticObjectTemplate template)
	{
		super(objectId);

		_template = template;
		reference = new L2Reference<StaticObjectInstance>(this);
	}

	@Override
	public HardReference<StaticObjectInstance> getRef()
	{
		return reference;
	}

	public int getUId()
	{
		return _template.getUId();
	}

	public int getType()
	{
		return _template.getType();
	}

	@Override
	public void onAction(Player player, boolean shift)
	{
		if(Events.onAction(player, this, shift))
			return;

		if(player.getTarget() != this)
		{
			player.setTarget(this);
			player.sendPacket(new MyTargetSelected(getObjectId(), 0));
			return;
		}

		MyTargetSelected my = new MyTargetSelected(getObjectId(), 0);
		player.sendPacket(my);

		if(!isInRange(player, 150))
		{
			if(player.getAI().getIntention() != CtrlIntention.AI_INTENTION_INTERACT)
				player.getAI().setIntention(CtrlIntention.AI_INTENTION_INTERACT, this, null);
			return;
		}

		if(_template.getType() == 0) // Arena Board
			player.sendPacket(new NpcHtmlMessage(player, getUId(), "newspaper/arena.htm", 0));
		else if(_template.getType() == 2) // Village map
		{
			player.sendPacket(new ShowTownMap(_template.getFilePath(), _template.getMapX(), _template.getMapY()));
			player.sendActionFailed();
		}
	}

	@Override
	public List<L2GameServerPacket> addPacketList(Player forPlayer, Creature dropper)
	{
		return Collections.<L2GameServerPacket>singletonList(new StaticObject(this));
	}

	@Override
	public boolean isAttackable(Creature attacker)
	{
		return false;
	}

	public void broadcastInfo(boolean force)
	{
		StaticObject p = new StaticObject(this);
		for(Player player : World.getAroundPlayers(this))
			player.sendPacket(p);
	}

	@Override
	public int getGeoZ(Location loc)   //FIXME [VISTALL] нужно ли?
	{
		return loc.z;
	}

	public int getMeshIndex()
	{
		return _meshIndex;
	}

	public void setMeshIndex(int meshIndex)
	{
		_meshIndex = meshIndex;
	}
}