package in.dragonbra.javasteam.steam.handlers;

import in.dragonbra.javasteam.TestBase;
import in.dragonbra.javasteam.TestPackets;
import in.dragonbra.javasteam.base.IClientMsg;
import in.dragonbra.javasteam.base.IPacketMsg;
import in.dragonbra.javasteam.enums.EMsg;
import in.dragonbra.javasteam.enums.EUniverse;
import in.dragonbra.javasteam.handlers.ClientMsgHandler;
import in.dragonbra.javasteam.steam.CMClient;
import in.dragonbra.javasteam.steam.steamclient.SteamClient;
import in.dragonbra.javasteam.steam.steamclient.callbackmgr.CallbackMsg;
import in.dragonbra.javasteam.steam.steamclient.configuration.SteamConfiguration;
import in.dragonbra.javasteam.types.JobID;
import in.dragonbra.javasteam.types.SteamID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.InetAddress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

/**
 * @author lngtr
 * @since 2018-03-24
 */
@ExtendWith(MockitoExtension.class)
public abstract class HandlerTestBase<T extends ClientMsgHandler> extends TestBase {

    protected static final JobID SOURCE_JOB_ID = new JobID(916351965);

    @Mock
    protected SteamClient steamClient;

    protected T handler;

    @BeforeEach
    void setUp() {
        handler = createHandler();
        handler.setup(steamClient);
        Mockito.lenient().when(steamClient.getSteamID()).thenReturn(new SteamID(123L));
        Mockito.lenient().when(steamClient.getConfiguration()).thenReturn(SteamConfiguration.createDefault());
        Mockito.lenient().when(steamClient.isConnected()).thenReturn(true);
        Mockito.lenient().when(steamClient.getNextJobID()).thenReturn(SOURCE_JOB_ID);
        Mockito.lenient().when(steamClient.getUniverse()).thenReturn(EUniverse.Public);
        Mockito.lenient().when(steamClient.getLocalIP()).thenReturn(InetAddress.getLoopbackAddress());
    }

    protected abstract T createHandler();

    protected <M extends IClientMsg> M verifySend(EMsg msgType) {
        ArgumentCaptor<IClientMsg> msgCaptor = ArgumentCaptor.forClass(IClientMsg.class);
        verify(steamClient, atLeast(1)).send(msgCaptor.capture());

        IClientMsg msg = msgCaptor.getValue();
        assertEquals(msgType, msg.getMsgType());

        //noinspection unchecked
        return (M) msg;
    }

    protected <C extends CallbackMsg> C verifyCallback() {
        ArgumentCaptor<CallbackMsg> callbackCaptor = ArgumentCaptor.forClass(CallbackMsg.class);
        verify(steamClient, atLeast(1)).postCallback(callbackCaptor.capture());

        CallbackMsg callback = callbackCaptor.getValue();

        //noinspection unchecked
        return (C) callback;
    }

    protected IPacketMsg getPacket(EMsg msgType, boolean isProto) {
        return CMClient.getPacketMsg(TestPackets.getPacket(msgType, isProto));
    }
}
