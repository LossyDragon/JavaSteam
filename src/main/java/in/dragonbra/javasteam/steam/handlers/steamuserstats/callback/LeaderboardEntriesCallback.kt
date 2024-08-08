package in.dragonbra.javasteam.steam.handlers.steamuserstats.callback;

import in.dragonbra.javasteam.base.ClientMsgProtobuf;
import in.dragonbra.javasteam.base.IPacketMsg;
import in.dragonbra.javasteam.enums.ELeaderboardDataRequest;
import in.dragonbra.javasteam.enums.EResult;
import in.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserverLbs.CMsgClientLBSGetLBEntriesResponse;
import in.dragonbra.javasteam.steam.handlers.steamuserstats.LeaderboardEntry;
import in.dragonbra.javasteam.steam.handlers.steamuserstats.SteamUserStats;
import in.dragonbra.javasteam.steam.steamclient.callbackmgr.CallbackMsg;
import in.dragonbra.javasteam.types.JobID;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * This callback is fired in response to {@link SteamUserStats#getLeaderboardEntries(int, int, int, int, ELeaderboardDataRequest)}.
 */
public class LeaderboardEntriesCallback extends CallbackMsg {

    private final EResult result;

    private final int entryCount;

    private final List<LeaderboardEntry> entries;

    public LeaderboardEntriesCallback(IPacketMsg packetMsg) {
        var msg = new ClientMsgProtobuf<CMsgClientLBSGetLBEntriesResponse.Builder>(
                CMsgClientLBSGetLBEntriesResponse.class, packetMsg);
        var resp = msg.getBody();

        setJobID(msg.getTargetJobID());

        result = EResult.from(resp.getEresult());
        entryCount = resp.getLeaderboardEntryCount();

        List<LeaderboardEntry> list = new ArrayList<>();

        for (CMsgClientLBSGetLBEntriesResponse.Entry entry : resp.getEntriesList()) {
            list.add(new LeaderboardEntry(entry));
        }

        entries = Collections.unmodifiableList(list);
    }

    /**
     * Gets the result of the request.
     *
     * @return the result of the request by {@link EResult}.
     */
    public EResult getResult() {
        return result;
    }

    /**
     * @return how many entries there are for requested leaderboard.
     */
    public int getEntryCount() {
        return entryCount;
    }

    /**
     * @return the list of leaderboard entries this response contains. See {@link LeaderboardEntry}
     */
    public List<LeaderboardEntry> getEntries() {
        return entries;
    }
}
