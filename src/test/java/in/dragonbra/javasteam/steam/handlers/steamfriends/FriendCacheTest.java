package in.dragonbra.javasteam.steam.handlers.steamfriends;

import in.dragonbra.javasteam.enums.EClanRelationship;
import in.dragonbra.javasteam.enums.EFriendRelationship;
import in.dragonbra.javasteam.enums.EPersonaState;
import in.dragonbra.javasteam.enums.EPersonaStateFlag;
import in.dragonbra.javasteam.steam.handlers.HandlerTestBase;
import in.dragonbra.javasteam.types.GameID;
import in.dragonbra.javasteam.types.SteamID;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author lossy
 * @since 2022-06-12
 */
public class FriendCacheTest extends HandlerTestBase<SteamFriends> {

    @Override
    protected SteamFriends createHandler() {
        return new SteamFriends();
    }

    @Test
    public void testAbstractAccountClass() {
        var abstractAccount = new FriendCache.User();

        var steamID = new SteamID(76561198003805806L);
        var avatarHash = "cfa928ab4119dd137e50d728e8fe703e4e970aff";

        // add random info to User.
        abstractAccount.setSteamID(steamID);
        abstractAccount.setName("Some Abstract Name");
        abstractAccount.setAvatarHash(avatarHash.getBytes());

        assertEquals(76561198003805806L, abstractAccount.getSteamID().convertToUInt64());
        assertEquals("Some Abstract Name", abstractAccount.getName());
        assertEquals(avatarHash, new String(abstractAccount.getAvatarHash()));
    }

    @Test
    public void testUserCacheAccount() {
        var user = new FriendCache.User();

        var gameID = new GameID(420, "Research and Development");
        var stateFlags = EnumSet.of(EPersonaStateFlag.ClientTypeVR);

        // add random info to User.
        user.setRelationship(EFriendRelationship.Friend);
        user.setPersonaState(EPersonaState.Online);
        user.setPersonaStateFlags(stateFlags);
        user.setGameAppID(420);
        user.setGameID(gameID);
        user.setGameName("Some Game");

        assertEquals(EFriendRelationship.Friend, user.getRelationship());
        assertEquals(EPersonaState.Online, user.getPersonaState());
        assertTrue(user.getPersonaStateFlags().contains(EPersonaStateFlag.ClientTypeVR));
        assertEquals(420, user.getGameAppID());
        assertTrue(user.getGameID().isMod());
        assertEquals(420, user.getGameID().getAppID());
        assertEquals(new GameID(0x8db24e81010001a4L), user.getGameID());
        assertEquals("Some Game", user.getGameName());
    }

    @Test
    public void testClanCacheAccount() {
        var clan = new FriendCache.Clan();

        // add random info to Clan
        clan.setRelationship(EClanRelationship.PendingApproval);

        assertEquals(EClanRelationship.PendingApproval, clan.getRelationship());
    }

    /**
     * Test coverage for {@link FriendCache.AccountCache}.
     * <p>
     * Mostly to verify both Type Erasure and ConcurrentHashMap work, see {@link FriendCache.AccountList}
     */
    @Test
    public void textAccountClass() {
        var accountCache = new FriendCache.AccountCache();

        /* Test Clan Stuff */
        var clan1 = new SteamID(103582791434671111L);
        var clan2 = new SteamID(103582791429522222L);
        var clan3 = new SteamID(103582791429523333L);

        assertTrue(clan1.isClanAccount());
        assertTrue(clan2.isClanAccount());
        assertTrue(clan3.isClanAccount());

        var clan1exist = accountCache.getClan(clan1);
        var clan2exist = accountCache.getClan(clan2);
        var clan3exist = accountCache.getClan(clan3);

        assertTrue(accountCache.getClans().contains(clan1exist));
        assertTrue(accountCache.getClans().contains(clan2exist));
        assertTrue(accountCache.getClans().contains(clan3exist));
        assertEquals(clan1,  clan1exist.getSteamID());
        assertEquals(clan2,  clan2exist.getSteamID());
        assertEquals(clan3,  clan3exist.getSteamID());

        assertEquals(3, accountCache.getClans().size());

        /* Test User Stuff */
        var user1 = new SteamID(76561197960265700L);
        var user2 = new SteamID(76561197960265711L);

        assertTrue(user1.isIndividualAccount());
        assertTrue(user2.isIndividualAccount());

        var user1Exist = accountCache.getUser(user1);
        var user2Exist = accountCache.getUser(user2);

        assertTrue(accountCache.getUsers().contains(user1Exist));
        assertTrue(accountCache.getUsers().contains(user2Exist));
        assertEquals(user1, user1Exist.getSteamID());
        assertEquals(user2, user2Exist.getSteamID());

        assertEquals(2, accountCache.getUsers().size());

        /* Test Local User */
        var localSteamID = steamClient.getSteamID();
        accountCache.getLocalUser().setSteamID(localSteamID);

        assertTrue(accountCache.isLocalUser(localSteamID));
        assertSame(localSteamID, accountCache.getLocalUser().getSteamID());
        assertSame(localSteamID, accountCache.getUser(localSteamID).getSteamID());
    }
}
