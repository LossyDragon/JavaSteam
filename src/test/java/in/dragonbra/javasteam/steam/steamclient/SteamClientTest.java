package in.dragonbra.javasteam.steam.steamclient;

import in.dragonbra.javasteam.base.IPacketMsg;
import in.dragonbra.javasteam.handlers.ClientMsgHandler;
import in.dragonbra.javasteam.steam.handlers.steamapps.SteamApps;
import in.dragonbra.javasteam.steam.handlers.steamcloud.SteamCloud;
import in.dragonbra.javasteam.steam.handlers.steamfriends.SteamFriends;
import in.dragonbra.javasteam.steam.handlers.steamgamecoordinator.SteamGameCoordinator;
import in.dragonbra.javasteam.steam.handlers.steamgameserver.SteamGameServer;
import in.dragonbra.javasteam.steam.handlers.steammasterserver.SteamMasterServer;
import in.dragonbra.javasteam.steam.handlers.steamscreenshots.SteamScreenshots;
import in.dragonbra.javasteam.steam.handlers.steamtrading.SteamTrading;
import in.dragonbra.javasteam.steam.handlers.steamuser.SteamUser;
import in.dragonbra.javasteam.steam.handlers.steamuserstats.SteamUserStats;
import in.dragonbra.javasteam.steam.handlers.steamworkshop.SteamWorkshop;
import org.junit.jupiter.api.Test;

import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

public class SteamClientTest {

    @Test
    public void constructorSetsInitialHandlers() {
        var steamClient = new SteamClient();
        assertNotNull(steamClient.getHandler(SteamUser.class));
        assertNotNull(steamClient.getHandler(SteamFriends.class));
        assertNotNull(steamClient.getHandler(SteamApps.class));
        assertNotNull(steamClient.getHandler(SteamGameCoordinator.class));
        assertNotNull(steamClient.getHandler(SteamGameServer.class));
        assertNotNull(steamClient.getHandler(SteamUserStats.class));
        assertNotNull(steamClient.getHandler(SteamMasterServer.class));
        assertNotNull(steamClient.getHandler(SteamCloud.class));
        assertNotNull(steamClient.getHandler(SteamWorkshop.class));
        assertNotNull(steamClient.getHandler(SteamTrading.class));
        // assertNotNull(steamClient.getHandler(SteamUnifiedMessages.class)); // TODO: Not implemented
        assertNotNull(steamClient.getHandler(SteamScreenshots.class));
    }

    @Test
    public void addHandlerAddsHandler() {
        var steamClient = new SteamClient();
        var handler = new TestMsgHandler();

        assertNull(steamClient.getHandler(TestMsgHandler.class));

        steamClient.addHandler(handler);
        assertEquals(handler, steamClient.getHandler(TestMsgHandler.class));
    }

    @Test
    public void removeHandlerRemovesHandler() {
        var steamClient = new SteamClient();

        steamClient.addHandler(new TestMsgHandler());
        assertNotNull(steamClient.getHandler(TestMsgHandler.class));

        steamClient.removeHandler(TestMsgHandler.class);
        assertNull(steamClient.getHandler(TestMsgHandler.class));
    }

    @Test
    public void getNextJobIDIsThreadsafe() {
        var steamClient = new SteamClient();
        var jobID = steamClient.getNextJobID();

        assertEquals(1L, jobID.getSequentialCount());

        IntStream.range(0, 1000).forEach(i -> steamClient.getNextJobID());

        jobID = steamClient.getNextJobID();
        assertEquals(1002L, jobID.getSequentialCount());
    }

    @Test
    public void getNextJobIDSetsProcessIDToZero() {
        var steamClient = new SteamClient();
        var jobID = steamClient.getNextJobID();

        assertEquals(0L, jobID.getProcessID());
    }

    // TODO not implemented
//    @Test
//    public void getNextJobIDFillsProcessStartTime() {
//    }

    @Test
    public void getNextJobIDSetsBoxIDToZero() {
        var steamClient = new SteamClient();
        var jobID = steamClient.getNextJobID();

        assertEquals(0L, jobID.getBoxID());
    }

    private static class TestMsgHandler extends ClientMsgHandler {
        @Override
        public void handleMsg(IPacketMsg packetMsg) {
        }
    }
}
