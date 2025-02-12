package l2ft.gameserver.network.l2.c2s;

import java.util.Calendar;

import l2ft.gameserver.Announcements;
import l2ft.gameserver.Config;
import l2ft.gameserver.autofarm.AutoFarmEngine;
import l2ft.gameserver.autofarm.AutoFarmState;
import l2ft.gameserver.dao.AutoFarmSkillDAO;
import l2ft.gameserver.dao.MailDAO;
import l2ft.gameserver.data.StringHolder;
import l2ft.gameserver.data.xml.holder.ResidenceHolder;
import l2ft.gameserver.instancemanager.CoupleManager;
import l2ft.gameserver.instancemanager.CursedWeaponsManager;
import l2ft.gameserver.instancemanager.PetitionManager;
import l2ft.gameserver.instancemanager.PlayerMessageStack;
import l2ft.gameserver.instancemanager.QuestManager;
import l2ft.gameserver.listener.actor.player.OnAnswerListener;
import l2ft.gameserver.listener.actor.player.impl.ReviveAnswerListener;
import l2ft.gameserver.model.Creature;
import l2ft.gameserver.model.Effect;
import l2ft.gameserver.model.GameObjectsStorage;
import l2ft.gameserver.model.Player;
import l2ft.gameserver.model.Skill;
import l2ft.gameserver.model.Summon;
import l2ft.gameserver.model.World;
import l2ft.gameserver.model.base.InvisibleType;
import l2ft.gameserver.model.entity.Hero;
import l2ft.gameserver.model.entity.SevenSigns;
import l2ft.gameserver.model.entity.events.impl.ClanHallAuctionEvent;
import l2ft.gameserver.model.entity.residence.ClanHall;
import l2ft.gameserver.model.items.ItemInstance;
import l2ft.gameserver.model.mail.Mail;
import l2ft.gameserver.model.pledge.Clan;
import l2ft.gameserver.model.pledge.SubUnit;
import l2ft.gameserver.model.pledge.UnitMember;
import l2ft.gameserver.model.quest.Quest;
import l2ft.gameserver.network.l2.GameClient;
import l2ft.gameserver.network.l2.components.SystemMsg;
import l2ft.gameserver.network.l2.s2c.ChangeWaitType;
import l2ft.gameserver.network.l2.s2c.ClientSetTime;
import l2ft.gameserver.network.l2.s2c.ConfirmDlg;
import l2ft.gameserver.network.l2.s2c.Die;
import l2ft.gameserver.network.l2.s2c.EtcStatusUpdate;
import l2ft.gameserver.network.l2.s2c.ExAutoSoulShot;
import l2ft.gameserver.network.l2.s2c.ExBR_PremiumState;
import l2ft.gameserver.network.l2.s2c.ExBasicActionList;
import l2ft.gameserver.network.l2.s2c.ExGoodsInventoryChangedNotify;
import l2ft.gameserver.network.l2.s2c.ExMPCCOpen;
import l2ft.gameserver.network.l2.s2c.ExNoticePostArrived;
import l2ft.gameserver.network.l2.s2c.ExNotifyPremiumItem;
import l2ft.gameserver.network.l2.s2c.ExPCCafePointInfo;
import l2ft.gameserver.network.l2.s2c.ExReceiveShowPostFriend;
import l2ft.gameserver.network.l2.s2c.ExSetCompassZoneCode;
import l2ft.gameserver.network.l2.s2c.ExStorageMaxCount;
import l2ft.gameserver.network.l2.s2c.HennaInfo;
import l2ft.gameserver.network.l2.s2c.L2FriendList;
import l2ft.gameserver.network.l2.s2c.L2GameServerPacket;
import l2ft.gameserver.network.l2.s2c.MagicSkillLaunched;
import l2ft.gameserver.network.l2.s2c.MagicSkillUse;
import l2ft.gameserver.network.l2.s2c.PartySmallWindowAll;
import l2ft.gameserver.network.l2.s2c.PartySpelled;
import l2ft.gameserver.network.l2.s2c.PetInfo;
import l2ft.gameserver.network.l2.s2c.PledgeShowInfoUpdate;
import l2ft.gameserver.network.l2.s2c.PledgeShowMemberListUpdate;
import l2ft.gameserver.network.l2.s2c.PledgeSkillList;
import l2ft.gameserver.network.l2.s2c.PrivateStoreMsgBuy;
import l2ft.gameserver.network.l2.s2c.PrivateStoreMsgSell;
import l2ft.gameserver.network.l2.s2c.QuestList;
import l2ft.gameserver.network.l2.s2c.RecipeShopMsg;
import l2ft.gameserver.network.l2.s2c.RelationChanged;
import l2ft.gameserver.network.l2.s2c.Ride;
import l2ft.gameserver.network.l2.s2c.SSQInfo;
import l2ft.gameserver.network.l2.s2c.ShortCutInit;
import l2ft.gameserver.network.l2.s2c.SkillCoolTime;
import l2ft.gameserver.network.l2.s2c.SkillList;
import l2ft.gameserver.network.l2.s2c.SocialAction;
import l2ft.gameserver.network.l2.s2c.SystemMessage2;
import l2ft.gameserver.skills.AbnormalEffect;
import l2ft.gameserver.tables.SkillTable;
import l2ft.gameserver.templates.item.ItemTemplate;
import l2ft.gameserver.utils.GameStats;
import l2ft.gameserver.utils.ItemFunctions;
import l2ft.gameserver.utils.TradeHelper;

import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EnterWorld extends L2GameClientPacket {
    private static final Object _lock = new Object();

    private static final Logger _log = LoggerFactory.getLogger(EnterWorld.class);

    @Override
    protected void readImpl() {
        // readS(); - клиент всегда отправляет строку "narcasse"
    }

    @Override
    protected void runImpl() {
        GameClient client = getClient();
        Player activeChar = client.getActiveChar();

        if (activeChar == null) {
            client.closeNow(false);
            return;
        }

        int MyObjectId = activeChar.getObjectId();
        Long MyStoreId = activeChar.getStoredId();

        synchronized (_lock)// TODO [G1ta0] че это за хуйня, и почему она тут
        {
            for (Player cha : GameObjectsStorage.getAllPlayersForIterate()) {
                if (MyStoreId == cha.getStoredId())
                    continue;
                try {
                    if (cha.getObjectId() == MyObjectId) {
                        _log.warn("Double EnterWorld for char: " + activeChar.getName());
                        cha.kick();
                    }
                } catch (Exception e) {
                    _log.error("", e);
                }
            }
        }

        GameStats.incrementPlayerEnterGame();

        boolean first = activeChar.entering;

        if (first) {
            activeChar.setOnlineStatus(true);
            if (activeChar.getPlayerAccess().GodMode && !Config.SHOW_GM_LOGIN)
                activeChar.setInvisibleType(InvisibleType.NORMAL);

            activeChar.setNonAggroTime(Long.MAX_VALUE);
            activeChar.spawnMe();

            if (activeChar.isInStoreMode())
                if (!TradeHelper.checksIfCanOpenStore(activeChar, activeChar.getPrivateStoreType())) {
                    activeChar.setPrivateStoreType(Player.STORE_PRIVATE_NONE);
                    activeChar.standUp();
                    activeChar.broadcastCharInfo();
                }

            activeChar.setRunning();
            activeChar.standUp();
            activeChar.startTimers();
        }

        activeChar.sendPacket(new ExBR_PremiumState(activeChar, activeChar.hasBonus()));

        activeChar.getMacroses().sendUpdate();
        activeChar.sendPacket(new SSQInfo(), new HennaInfo(activeChar));
        activeChar.sendItemList(false);
        activeChar.sendPacket(new ShortCutInit(activeChar), new SkillList(activeChar), new SkillCoolTime(activeChar));
        activeChar.sendPacket(SystemMsg.WELCOME_TO_THE_WORLD_OF_LINEAGE_II);

        Announcements.getInstance().showAnnouncements(activeChar);

        if (first)
            activeChar.getListeners().onEnter();

        SevenSigns.getInstance().sendCurrentPeriodMsg(activeChar);

        if (first && activeChar.getCreateTime() > 0) {
            Calendar create = Calendar.getInstance();
            create.setTimeInMillis(activeChar.getCreateTime());
            Calendar now = Calendar.getInstance();

            int day = create.get(Calendar.DAY_OF_MONTH);
            if (create.get(Calendar.MONTH) == Calendar.FEBRUARY && day == 29)
                day = 28;

            int myBirthdayReceiveYear = activeChar.getVarInt(Player.MY_BIRTHDAY_RECEIVE_YEAR, 0);
            if (create.get(Calendar.MONTH) == now.get(Calendar.MONTH) && create.get(Calendar.DAY_OF_MONTH) == day) {
                if ((myBirthdayReceiveYear == 0 && create.get(Calendar.YEAR) != now.get(Calendar.YEAR))
                        || myBirthdayReceiveYear > 0 && myBirthdayReceiveYear != now.get(Calendar.YEAR)) {
                    Mail mail = new Mail();
                    mail.setSenderId(1);
                    mail.setSenderName(StringHolder.getInstance().getNotNull(activeChar, "birthday.npc"));
                    mail.setReceiverId(activeChar.getObjectId());
                    mail.setReceiverName(activeChar.getName());
                    mail.setTopic(StringHolder.getInstance().getNotNull(activeChar, "birthday.title"));
                    mail.setBody(StringHolder.getInstance().getNotNull(activeChar, "birthday.text"));

                    ItemInstance item = ItemFunctions.createItem(21169);
                    item.setLocation(ItemInstance.ItemLocation.MAIL);
                    item.setCount(1L);
                    item.save();

                    mail.addAttachment(item);
                    mail.setUnread(true);
                    mail.setType(Mail.SenderType.BIRTHDAY);
                    mail.setExpireTime(720 * 3600 + (int) (System.currentTimeMillis() / 1000L));
                    mail.save();

                    activeChar.setVar(Player.MY_BIRTHDAY_RECEIVE_YEAR, String.valueOf(now.get(Calendar.YEAR)), -1);
                }
            }
        }

        if (activeChar.getClan() != null) {
            notifyClanMembers(activeChar);

            activeChar.sendPacket(activeChar.getClan().listAll());
            activeChar.sendPacket(new PledgeShowInfoUpdate(activeChar.getClan()),
                    new PledgeSkillList(activeChar.getClan()));
        }

        // engage and notify Partner
        if (first && Config.ALLOW_WEDDING) {
            CoupleManager.getInstance().engage(activeChar);
            CoupleManager.getInstance().notifyPartner(activeChar);
        }

        if (first) {
            activeChar.getFriendList().notifyFriends(true);
            loadTutorial(activeChar);
            activeChar.restoreDisableSkills();
        }

        sendPacket(new L2FriendList(activeChar), new ExStorageMaxCount(activeChar), new QuestList(activeChar),
                new ExBasicActionList(activeChar), new EtcStatusUpdate(activeChar));

        activeChar.checkHpMessages(activeChar.getMaxHp(), activeChar.getCurrentHp());
        activeChar.checkDayNightMessages();

        if (Config.PETITIONING_ALLOWED)
            PetitionManager.getInstance().checkPetitionMessages(activeChar);

        if (!first) {
            if (activeChar.isCastingNow()) {
                Creature castingTarget = activeChar.getCastingTarget();
                Skill castingSkill = activeChar.getCastingSkill();
                long animationEndTime = activeChar.getAnimationEndTime();
                if (castingSkill != null && castingTarget != null && castingTarget.isCreature()
                        && activeChar.getAnimationEndTime() > 0)
                    sendPacket(new MagicSkillUse(activeChar, castingTarget, castingSkill.getId(),
                            castingSkill.getLevel(), (int) (animationEndTime - System.currentTimeMillis()), 0));
            }

            if (activeChar.isInBoat())
                activeChar.sendPacket(activeChar.getBoat().getOnPacket(activeChar, activeChar.getInBoatPosition()));

            if (activeChar.isMoving || activeChar.isFollow)
                sendPacket(activeChar.movePacket());

            if (activeChar.getMountNpcId() != 0)
                sendPacket(new Ride(activeChar));

            if (activeChar.isFishing())
                activeChar.stopFishing();
        }

        activeChar.entering = false;
        activeChar.sendUserInfo(true);

        if (activeChar.isSitting())
            activeChar.sendPacket(new ChangeWaitType(activeChar, ChangeWaitType.WT_SITTING));
        if (activeChar.getPrivateStoreType() != Player.STORE_PRIVATE_NONE)
            if (activeChar.getPrivateStoreType() == Player.STORE_PRIVATE_BUY)
                sendPacket(new PrivateStoreMsgBuy(activeChar));
            else if (activeChar.getPrivateStoreType() == Player.STORE_PRIVATE_SELL
                    || activeChar.getPrivateStoreType() == Player.STORE_PRIVATE_SELL_PACKAGE)
                sendPacket(new PrivateStoreMsgSell(activeChar));
            else if (activeChar.getPrivateStoreType() == Player.STORE_PRIVATE_MANUFACTURE)
                sendPacket(new RecipeShopMsg(activeChar));

        if (activeChar.isDead())
            sendPacket(new Die(activeChar));

        activeChar.unsetVar("offline");

        // на всякий случай
        activeChar.sendActionFailed();

        if (first && activeChar.isGM() && Config.SAVE_GM_EFFECTS && activeChar.getPlayerAccess().CanUseGMCommand) {
            // silence
            if (activeChar.getVarB("gm_silence")) {
                activeChar.setMessageRefusal(true);
                activeChar.sendPacket(SystemMsg.MESSAGE_REFUSAL_MODE);
            }
            // invul
            if (activeChar.getVarB("gm_invul")) {
                activeChar.setIsInvul(true);
                activeChar.startAbnormalEffect(AbnormalEffect.S_INVULNERABLE);
                activeChar.sendMessage(activeChar.getName() + " is now immortal.");
            }
            // gmspeed
            try {
                int var_gmspeed = Integer.parseInt(activeChar.getVar("gm_gmspeed"));
                if (var_gmspeed >= 1 && var_gmspeed <= 4)
                    activeChar.doCast(SkillTable.getInstance().getInfo(7029, var_gmspeed), activeChar, true);
            } catch (Exception E) {
            }
        }

        PlayerMessageStack.getInstance().CheckMessages(activeChar);

        sendPacket(ClientSetTime.STATIC, new ExSetCompassZoneCode(activeChar));

        Pair<Integer, OnAnswerListener> entry = activeChar.getAskListener(false);
        if (entry != null && entry.getValue() instanceof ReviveAnswerListener)
            sendPacket(new ConfirmDlg(
                    SystemMsg.C1_IS_MAKING_AN_ATTEMPT_TO_RESURRECT_YOU_IF_YOU_CHOOSE_THIS_PATH_S2_EXPERIENCE_WILL_BE_RETURNED_FOR_YOU,
                    0).addString("Other player").addString("some"));

        if (activeChar.isCursedWeaponEquipped())
            CursedWeaponsManager.getInstance().showUsageTime(activeChar, activeChar.getCursedWeaponEquippedId());

        if (!first) {
            // Персонаж вылетел во время просмотра
            if (activeChar.isInObserverMode()) {
                if (activeChar.getObserverMode() == Player.OBSERVER_LEAVING)
                    activeChar.returnFromObserverMode();
                else if (activeChar.getOlympiadObserveGame() != null)
                    activeChar.leaveOlympiadObserverMode(true);
                else
                    activeChar.leaveObserverMode();
            } else if (activeChar.isVisible())
                World.showObjectsToPlayer(activeChar);

            if (activeChar.getPet() != null)
                sendPacket(new PetInfo(activeChar.getPet()));

            if (activeChar.isInParty()) {
                Summon member_pet;
                // sends new member party window for all members
                // we do all actions before adding member to a list, this speeds things up a
                // little
                sendPacket(new PartySmallWindowAll(activeChar.getParty(), activeChar));

                for (Player member : activeChar.getParty().getPartyMembers())
                    if (member != activeChar) {
                        sendPacket(new PartySpelled(member, true));
                        if ((member_pet = member.getPet()) != null)
                            sendPacket(new PartySpelled(member_pet, true));

                        sendPacket(RelationChanged.update(activeChar, member, activeChar));
                    }

                // Если партия уже в СС, то вновь прибывшем посылаем пакет открытия окна СС
                if (activeChar.getParty().isInCommandChannel())
                    sendPacket(ExMPCCOpen.STATIC);
            }

            for (int shotId : activeChar.getAutoSoulShot())
                sendPacket(new ExAutoSoulShot(shotId, true));

            for (Effect e : activeChar.getEffectList().getAllFirstEffects())
                if (e.getSkill().isToggle())
                    sendPacket(new MagicSkillLaunched(activeChar.getObjectId(), e.getSkill().getId(),
                            e.getSkill().getLevel(), activeChar));

            activeChar.broadcastCharInfo();
        } else
            activeChar.sendUserInfo(); // Отобразит права в клане

        activeChar.updateEffectIcons();
        activeChar.updateStats();

        if (Config.ALT_PCBANG_POINTS_ENABLED)
            activeChar.sendPacket(new ExPCCafePointInfo(activeChar, 0, 1, 2, 12));

        if (!activeChar.getPremiumItemList().isEmpty())
            activeChar.sendPacket(
                    Config.GOODS_INVENTORY_ENABLED ? ExGoodsInventoryChangedNotify.STATIC : ExNotifyPremiumItem.STATIC);

        if (activeChar.getVarB("HeroPeriod") && Config.SERVICES_HERO_SELL_ENABLED) {
            activeChar.setHero(true);
            Hero.addSkills(activeChar);
            if (activeChar.isHero())
                activeChar.broadcastPacket(new SocialAction(activeChar.getObjectId(), 16));
            activeChar.broadcastUserInfo(true);
        }

        activeChar.sendVoteSystemInfo();
        activeChar.sendPacket(new ExReceiveShowPostFriend(activeChar));
        activeChar.getNevitSystem().onEnterWorld();

        AutoFarmState farmState = AutoFarmEngine.getInstance().getOrCreateState(activeChar);
        AutoFarmSkillDAO.getInstance().loadSkillsForStackClass(activeChar, farmState);

        checkNewMail(activeChar);
    }

    private static void notifyClanMembers(Player activeChar) {
        Clan clan = activeChar.getClan();
        SubUnit subUnit = activeChar.getSubUnit();
        if (clan == null || subUnit == null)
            return;

        UnitMember member = subUnit.getUnitMember(activeChar.getObjectId());
        if (member == null)
            return;

        member.setPlayerInstance(activeChar, false);

        int sponsor = activeChar.getSponsor();
        int apprentice = activeChar.getApprentice();
        L2GameServerPacket msg = new SystemMessage2(SystemMsg.CLAN_MEMBER_S1_HAS_LOGGED_INTO_GAME).addName(activeChar);
        PledgeShowMemberListUpdate memberUpdate = new PledgeShowMemberListUpdate(activeChar);
        for (Player clanMember : clan.getOnlineMembers(activeChar.getObjectId())) {
            clanMember.sendPacket(memberUpdate);
            if (clanMember.getObjectId() == sponsor)
                clanMember.sendPacket(
                        new SystemMessage2(SystemMsg.YOUR_APPRENTICE_C1_HAS_LOGGED_OUT).addName(activeChar));
            else if (clanMember.getObjectId() == apprentice)
                clanMember.sendPacket(new SystemMessage2(SystemMsg.YOUR_SPONSOR_C1_HAS_LOGGED_IN).addName(activeChar));
            else
                clanMember.sendPacket(msg);
        }

        if (!activeChar.isClanLeader())
            return;

        ClanHall clanHall = clan.getHasHideout() > 0
                ? ResidenceHolder.getInstance().getResidence(ClanHall.class, clan.getHasHideout())
                : null;
        if (clanHall == null || clanHall.getAuctionLength() != 0)
            return;

        if (clanHall.getSiegeEvent().getClass() != ClanHallAuctionEvent.class)
            return;

        if (clan.getWarehouse().getCountOf(ItemTemplate.ITEM_ID_ADENA) < clanHall.getRentalFee())
            activeChar.sendPacket(new SystemMessage2(
                    SystemMsg.PAYMENT_FOR_YOUR_CLAN_HALL_HAS_NOT_BEEN_MADE_PLEASE_ME_PAYMENT_TO_YOUR_CLAN_WAREHOUSE_BY_S1_TOMORROW)
                    .addLong(clanHall.getRentalFee()));
    }

    private void loadTutorial(Player player) {
        Quest q = QuestManager.getQuest(255);
        if (q != null)
            player.processQuestEvent(q.getName(), "UC", null);
    }

    private void checkNewMail(Player activeChar) {
        for (Mail mail : MailDAO.getInstance().getReceivedMailByOwnerId(activeChar.getObjectId()))
            if (mail.isUnread()) {
                sendPacket(ExNoticePostArrived.STATIC_FALSE);
                break;
            }
    }
}