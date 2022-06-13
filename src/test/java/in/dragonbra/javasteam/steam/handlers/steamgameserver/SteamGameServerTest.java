package in.dragonbra.javasteam.steam.handlers.steamgameserver;

import in.dragonbra.javasteam.enums.EResult;
import in.dragonbra.javasteam.steam.handlers.HandlerTestBase;
import in.dragonbra.javasteam.steam.handlers.steamuser.callback.LoggedOnCallback;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

/**
 * @author lossy
 * @since 2022-06-12
 */
public class SteamGameServerTest extends HandlerTestBase<SteamGameServer> {

    @Override
    protected SteamGameServer createHandler() {
        return new SteamGameServer();
    }

    @Test
    public void logOnPostsLoggedOnCallbackWhenNoConnection() {
        reset(steamClient);
        when(steamClient.isConnected()).thenReturn(false);

        LogOnDetails details = new LogOnDetails();
        details.setToken("SuperSecretToken");

        handler.logOn(details);

        var callback = verifyCallback();
        assertNotNull(callback);
        assertInstanceOf(LoggedOnCallback.class, callback);

        var loc = (LoggedOnCallback) callback;
        assertEquals(EResult.NoConnection, loc.getResult());
    }

    @Test
    public void logOnAnonymousPostsLoggedOnCallbackWhenNoConnection() {
        reset(steamClient);
        when(steamClient.isConnected()).thenReturn(false);

        handler.logOnAnonymous();

        var callback = verifyCallback();
        assertNotNull(callback);
        assertInstanceOf(LoggedOnCallback.class, callback);

        var loc = (LoggedOnCallback) callback;
        assertEquals(EResult.NoConnection, loc.getResult());
    }
}
