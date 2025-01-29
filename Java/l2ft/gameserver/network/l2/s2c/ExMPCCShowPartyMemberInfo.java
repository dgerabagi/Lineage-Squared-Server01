package l2ft.gameserver.network.l2.s2c;

import java.util.ArrayList;
import java.util.List;

import l2ft.gameserver.model.Party;
import l2ft.gameserver.model.Player;


/**
 * Format: ch d[Sdd]
 * @author SYS
 */
public class ExMPCCShowPartyMemberInfo extends L2GameServerPacket
{
	private List<PartyMemberInfo> members;

	public ExMPCCShowPartyMemberInfo(Party party)
	{
		members = new ArrayList<PartyMemberInfo>();
		for(Player _member : party.getPartyMembers())
			members.add(new PartyMemberInfo(_member.getName(), _member.getObjectId(), _member.getActiveClass().getFirstClassId()));
	}

	@Override
	protected final void writeImpl()
	{
		writeEx(0x4b);
		writeD(members.size()); // ĐšĐľĐ»Đ¸Ń‡ĐµŃ�Ń‚Đ˛Đľ Ń‡Đ»ĐµĐ˝ĐľĐ˛ Đ˛ ĐżĐ°Ń‚Đ¸

		for(PartyMemberInfo member : members)
		{
			writeS(member.name); // Đ�ĐĽŃŹ Ń‡Đ»ĐµĐ˝Đ° ĐżĐ°Ń‚Đ¸
			writeD(member.object_id); // object Id Ń‡Đ»ĐµĐ˝Đ° ĐżĐ°Ń‚Đ¸
			writeD(member.class_id); // id ĐşĐ»Đ°Ń�Ń�Đ° Ń‡Đ»ĐµĐ˝Đ° ĐżĐ°Ń‚Đ¸
		}

		members.clear();
	}

	static class PartyMemberInfo
	{
		public String name;
		public int object_id, class_id;

		public PartyMemberInfo(String _name, int _object_id, int _class_id)
		{
			name = _name;
			object_id = _object_id;
			class_id = _class_id;
		}
	}
}