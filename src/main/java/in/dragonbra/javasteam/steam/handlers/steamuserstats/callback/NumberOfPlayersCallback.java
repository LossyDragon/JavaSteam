package in.dragonbra.javasteam.steam.handlers.steamuserstats.callback;

import in.dragonbra.javasteam.base.ClientMsgProtobuf;
import in.dragonbra.javasteam.base.IPacketMsg;
import in.dragonbra.javasteam.enums.EResult;
import in.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserver2.CMsgDPGetNumberOfCurrentPlayersResponse;
import in.dragonbra.javasteam.steam.handlers.steamuserstats.SteamUserStats;
import in.dragonbra.javasteam.steam.steamclient.callbackmgr.CallbackMsg;
import in.dragonbra.javasteam.types.JobID;

/**
 * This callback is fired in response to {@link SteamUserStats#getNumberOfCurrentPlayers(int)}.
 */
public class NumberOfPlayersCallback extends CallbackMsg {

    private final EResult result;

    private final int numPlayers;

    public NumberOfPlayersCallback(IPacketMsg packetMsg) {
        var msg = new ClientMsgProtobuf<CMsgDPGetNumberOfCurrentPlayersResponse.Builder>(
                CMsgDPGetNumberOfCurrentPlayersResponse.class, packetMsg);
        var resp = msg.getBody();

        setJobID(msg.getTargetJobID());
        result = EResult.from(resp.getEresult());
        numPlayers = resp.getPlayerCount();
    }

    /**
     * @return the result of the request by {@link EResult}.
     */
    public EResult getResult() {
        return result;
    }

    /**
     * @return the current number of players according to Steam.
     */
    public int getNumPlayers() {
        return numPlayers;
    }
}
