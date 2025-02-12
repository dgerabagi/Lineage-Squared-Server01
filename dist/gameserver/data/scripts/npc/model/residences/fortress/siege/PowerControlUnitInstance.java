package npc.model.residences.fortress.siege;

import java.util.StringTokenizer;

import l2ft.commons.util.Rnd;
import l2ft.gameserver.model.Player;
import l2ft.gameserver.model.Spawner;
import l2ft.gameserver.model.entity.events.impl.FortressSiegeEvent;
import l2ft.gameserver.model.entity.events.objects.SpawnExObject;
import l2ft.gameserver.model.instances.NpcInstance;
import l2ft.gameserver.network.l2.components.NpcString;
import l2ft.gameserver.network.l2.s2c.NpcHtmlMessage;
import l2ft.gameserver.templates.npc.NpcTemplate;

import org.apache.commons.lang3.ArrayUtils;

/**
 * @author VISTALL
 * @date 19:35/19.04.2011
 */
public class PowerControlUnitInstance extends NpcInstance
{
	public static final int LIMIT = 3;

	public static final int COND_NO_ENTERED = 0;
	public static final int COND_ENTERED = 1;
	public static final int COND_ALL_OK = 2;
	public static final int COND_FAIL = 3;
	public static final int COND_TIMEOUT = 4;

	private int[] _generated = new int[LIMIT];
	private int _index;
	private int _tryCount;
	private long _invalidatePeriod;

	public PowerControlUnitInstance(int objectId, NpcTemplate template)
	{
		super(objectId, template);
	}

	@Override
	public void onBypassFeedback(Player player, String command)
	{
		if(!canBypassCheck(player, this))
			return;

		StringTokenizer token = new StringTokenizer(command);
		token.nextToken();    // step

		if(_tryCount == 0)
			_tryCount ++;
		else
			_index ++;

		showChatWindow(player, 0);
	}


	@Override
	public void onSpawn()
	{
		super.onSpawn();

		generate();
	}

	@Override
	public void showChatWindow(Player player, int val, Object... arg)
	{
		NpcHtmlMessage message = new NpcHtmlMessage(player, this);

		if(_invalidatePeriod > 0 && _invalidatePeriod < System.currentTimeMillis())
			generate();

		int cond = getCond();
		switch(cond)
		{
			case COND_ALL_OK:
				message.setFile("residence2/fortress/fortress_inner_controller002.htm");

				FortressSiegeEvent event = getEvent(FortressSiegeEvent.class);
				if(event != null)
				{
					SpawnExObject exObject = event.getFirstObject(FortressSiegeEvent.SIEGE_COMMANDERS);
					Spawner spawn = exObject.getSpawns().get(3); // spawn of Main Machine

					MainMachineInstance machineInstance = (MainMachineInstance)spawn.getFirstSpawned();
					machineInstance.powerOff(this);

					onDecay();
				}
			break;
			case COND_TIMEOUT:
				message.setFile("residence2/fortress/fortress_inner_controller003.htm");
				break;
			case COND_FAIL:
				message.setFile("residence2/fortress/fortress_inner_controller003.htm");
				_invalidatePeriod = System.currentTimeMillis() + 30000L;
				break;
			case COND_ENTERED:
				message.setFile("residence2/fortress/fortress_inner_controller004.htm") ;
				message.replaceNpcString("%password%", _index == 0 ? NpcString.PASSWORD_HAS_NOT_BEEN_ENTERED : _index == 1 ? NpcString.FIRST_PASSWORD_HAS_BEEN_ENTERED : NpcString.SECOND_PASSWORD_HAS_BEEN_ENTERED);
				message.replaceNpcString("%try_count%", NpcString.ATTEMPT_S1__3_IS_IN_PROGRESS, _tryCount);
				break;
			case COND_NO_ENTERED:
				message.setFile("residence2/fortress/fortress_inner_controller001.htm");
				break;
		}
		player.sendPacket(message);
	}

	private void generate()
	{
		_invalidatePeriod = 0;
		_tryCount = 0;
		_index = 0;

		for(int i = 0; i < _generated.length; i++)
			_generated[i] = -1;

		int j = 0;
		while(j != LIMIT)
		{
			int val = Rnd.get(0, 9);
			if(ArrayUtils.contains(_generated, val))
				continue;
			_generated[j ++] = val;
		}
	}

	private int getCond()
	{
		if(_invalidatePeriod > System.currentTimeMillis())
			return COND_TIMEOUT;
		else if(_tryCount >= LIMIT)  // ĐĽĐ°ĐşŃ�Đ¸ĐĽŃ�ĐĽ Đ»Đ¸ĐĽĐ¸Ń‚
			return COND_FAIL;
		else if(_index == 0 && _tryCount == 0)  // Đ¸Ń‰Đľ Đ˝Đ¸Ń‡ĐµĐłĐľ Đ˝Đ¸ĐşŃ‚Đľ Đ˝Đµ ĐşĐ»Đ°Ń†Đ°Đ»
			return COND_NO_ENTERED;
		else if(_index == LIMIT)   // Đ˛Ń�Đµ Đ˛ĐµŃ€Đ˝Đľ
			return COND_ALL_OK;
		else // Đ˝Đµ Đ˛Ń�Đµ Ń�Đ´Đ°Đ»
			return COND_ENTERED;
	}

	public int[] getGenerated()
	{
		return _generated;
	}
}
