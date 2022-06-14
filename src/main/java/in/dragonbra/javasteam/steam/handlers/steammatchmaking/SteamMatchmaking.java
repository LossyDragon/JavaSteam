package in.dragonbra.javasteam.steam.handlers.steammatchmaking;

import com.google.protobuf.AbstractMessage;
import com.google.protobuf.ByteString;
import in.dragonbra.javasteam.base.ClientMsgProtobuf;
import in.dragonbra.javasteam.base.IPacketMsg;
import in.dragonbra.javasteam.enums.EChatRoomEnterResponse;
import in.dragonbra.javasteam.enums.ELobbyType;
import in.dragonbra.javasteam.enums.EMsg;
import in.dragonbra.javasteam.enums.EResult;
import in.dragonbra.javasteam.handlers.ClientMsgHandler;
import in.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserverMms;
import in.dragonbra.javasteam.steam.handlers.steamfriends.SteamFriends;
import in.dragonbra.javasteam.steam.handlers.steammatchmaking.callback.*;
import in.dragonbra.javasteam.types.JobID;
import in.dragonbra.javasteam.types.SteamID;
import in.dragonbra.javasteam.util.NetHelpers;
import in.dragonbra.javasteam.util.compat.Consumer;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class SteamMatchmaking extends ClientMsgHandler {

    private Map<EMsg, Consumer<IPacketMsg>> dispatchMap;

    private final ConcurrentHashMap<JobID, AbstractMessage> lobbyManipulationRequests = new ConcurrentHashMap<>();

    private final LobbyCache lobbyCache = new LobbyCache();

    public SteamMatchmaking() {
        dispatchMap = new HashMap<>();

        dispatchMap.put(EMsg.ClientMMSCreateLobbyResponse, this::handleCreateLobbyResponse);
        dispatchMap.put(EMsg.ClientMMSSetLobbyDataResponse, this::handleSetLobbyDataResponse);
        dispatchMap.put(EMsg.ClientMMSSetLobbyOwnerResponse, this::handleSetLobbyOwnerResponse);
        dispatchMap.put(EMsg.ClientMMSLobbyData, this::handleLobbyData);
        dispatchMap.put(EMsg.ClientMMSGetLobbyListResponse, this::handleGetLobbyListResponse);
        dispatchMap.put(EMsg.ClientMMSJoinLobbyResponse, this::handleJoinLobbyResponse);
        dispatchMap.put(EMsg.ClientMMSLeaveLobbyResponse, this::handleLeaveLobbyResponse);
        dispatchMap.put(EMsg.ClientMMSUserJoinedLobby, this::handleUserJoinedLobby);
        dispatchMap.put(EMsg.ClientMMSUserLeftLobby, this::handleUserLeftLobby);

        dispatchMap = Collections.unmodifiableMap(dispatchMap);
    }

    /**
     * Sends a request to create a new lobby.
     * <p>
     * Returns nothing if the request could not be submitted i.e. not yet logged in. Otherwise, an {@link CreateLobbyCallback}.
     *
     * @param appId      ID of the app the lobby will belong to.
     * @param lobbyType  The new lobby type.
     * @param maxMembers The new maximum number of members that may occupy the lobby.
     * @param lobbyFlags The new lobby flags. Defaults to 0.
     * @param metadata   The new metadata for the lobby. Defaults to <strong>null</strong> (treated as an empty dictionary).
     */
    public void createLobby(int appId, ELobbyType lobbyType, int maxMembers,
                            Integer lobbyFlags, HashMap<String, String> metadata) {
        if (client.getCellID() == null) {
            return;
        }

        var personaName = client.getHandler(SteamFriends.class).getPersonaName();

        var createLobby = new ClientMsgProtobuf<SteammessagesClientserverMms.CMsgClientMMSCreateLobby.Builder>
                (SteammessagesClientserverMms.CMsgClientMMSCreateLobby.class, EMsg.ClientMMSCreateLobby);

        var body = createLobby
                .getBody()
                .setAppId(appId)
                .setLobbyType(lobbyType.code())
                .setMaxMembers(maxMembers)
                .setLobbyFlags(lobbyFlags != null ? lobbyFlags : 0)
                .setMetadata(ByteString.copyFrom(Lobby.encodeMetadata(metadata)))
                .setCellId(client.getCellID())
                .setPublicIp(NetHelpers.getMsgIPAddress(client.getPublicIP()))
                .setPersonaNameOwner(personaName);

        createLobby.setBody(body);
        createLobby.getProtoHeader().setJobidSource(client.getNextJobID().getValue());

        send(createLobby, appId);

        lobbyManipulationRequests.put(createLobby.getSourceJobID(), createLobby.getBody().build());
    }

    /**
     * Sends a request to update a lobby.
     * <p>
     * Returns {@link SetLobbyDataCallback}.
     *
     * @param appId        ID of app the lobby belongs to.
     * @param lobbySteamId The SteamID of the lobby that should be updated.
     * @param lobbyType    The new lobby type.
     * @param maxMembers   The new maximum number of members that may occupy the lobby.
     * @param lobbyFlags   The new lobby flags. Defaults to 0.
     * @param metadata     The new metadata for the lobby. Defaults to <strong>null</strong> (treated as an empty dictionary).
     */
    public void setLobbyData(int appId, SteamID lobbySteamId, ELobbyType lobbyType,
                             int maxMembers, Integer lobbyFlags, HashMap<String, String> metadata) {
        var setLobbyData = new ClientMsgProtobuf<SteammessagesClientserverMms.CMsgClientMMSSetLobbyData.Builder>
                (SteammessagesClientserverMms.CMsgClientMMSSetLobbyData.class, EMsg.ClientMMSSetLobbyData);

        var body = setLobbyData
                .getBody()
                .setAppId(appId)
                .setSteamIdLobby(lobbySteamId.convertToUInt64())
                .setSteamIdMember(0)
                .setLobbyType(lobbyType.code())
                .setMaxMembers(maxMembers)
                .setLobbyFlags((lobbyFlags != null) ? lobbyFlags : 0)
                .setMetadata(ByteString.copyFrom(Lobby.encodeMetadata(metadata)));

        setLobbyData.setBody(body);
        setLobbyData.setSourceJobID(client.getNextJobID());

        send(setLobbyData, appId);

        lobbyManipulationRequests.put(setLobbyData.getSourceJobID(), setLobbyData.getBody().build());
    }

    /**
     * Sends a request to update the current user's lobby metadata.
     * <p>
     * Returns nothing if the request could not be submitted i.e. not yet logged in.
     * Otherwise, an {@link SetLobbyDataCallback}
     *
     * @param appId        ID of app the lobby belongs to.
     * @param lobbySteamId The SteamID of the lobby that should be updated.
     * @param metadata     The new metadata for the lobby.
     */
    public void setLobbyMemberData(int appId, SteamID lobbySteamId, HashMap<String, String> metadata) {
        if (client.getSteamID() == null) {
            return;
        }

        var setLobbyData = new ClientMsgProtobuf<SteammessagesClientserverMms.CMsgClientMMSSetLobbyData.Builder>
                (SteammessagesClientserverMms.CMsgClientMMSSetLobbyData.class, EMsg.ClientMMSSetLobbyData);

        var body = setLobbyData.getBody()
                .setAppId(appId)
                .setSteamIdLobby(lobbySteamId.convertToUInt64())
                .setSteamIdMember(client.getSteamID().convertToUInt64())
                .setMetadata(ByteString.copyFrom(Lobby.encodeMetadata(metadata)));

        setLobbyData.setBody(body);
        setLobbyData.setSourceJobID(client.getNextJobID());

        send(setLobbyData, appId);

        lobbyManipulationRequests.put(setLobbyData.getSourceJobID(), setLobbyData.getBody().build());
    }

    /**
     * Sends a request to update the owner of a lobby.
     * <p>
     * Returns an {@link  SetLobbyOwnerCallback}
     *
     * @param appId        ID of app the lobby belongs to.
     * @param lobbySteamId The SteamID of the lobby that should have its owner updated.
     * @param newOwner     The SteamID of the new owner.
     */
    public void setLobbyOwner(int appId, SteamID lobbySteamId, SteamID newOwner) {
        var setLobbyOwner = new ClientMsgProtobuf<SteammessagesClientserverMms.CMsgClientMMSSetLobbyOwner.Builder>
                (SteammessagesClientserverMms.CMsgClientMMSSetLobbyOwner.class, EMsg.ClientMMSSetLobbyOwner);

        var body = setLobbyOwner
                .getBody()
                .setAppId(appId)
                .setSteamIdLobby(lobbySteamId.convertToUInt64())
                .setSteamIdNewOwner(newOwner.convertToUInt64());

        setLobbyOwner.setBody(body);
        setLobbyOwner.setSourceJobID(client.getNextJobID());

        send(setLobbyOwner, appId);

        lobbyManipulationRequests.put(setLobbyOwner.getSourceJobID(), setLobbyOwner.getBody().build());
    }

    /**
     * Sends a request to obtains a list of lobbies matching the specified criteria.
     * <p>
     * Returns nothing if the request could not be submitted i.e. not yet logged in.
     * Otherwise, an {@link  GetLobbyListCallback}.
     *
     * @param appId      The ID of app for which we're requesting a list of lobbies.
     * @param filters    An optional list of filters.
     * @param maxLobbies An optional maximum number of lobbies that will be returned.
     */
    public void getLobbyList(int appId, List<Lobby.Filter> filters, Integer maxLobbies) {
        if (client.getCellID() == null) {
            return;
        }

        var getLobbies = new ClientMsgProtobuf<SteammessagesClientserverMms.CMsgClientMMSGetLobbyList.Builder>
                (SteammessagesClientserverMms.CMsgClientMMSGetLobbyList.class, EMsg.ClientMMSGetLobbyList);

        var body = getLobbies
                .getBody()
                .setAppId(appId)
                .setCellId(client.getCellID())
                .setPublicIp(NetHelpers.getMsgIPAddress(client.getPublicIP()))
                .setNumLobbiesRequested((maxLobbies != null) ? maxLobbies : -1);

        getLobbies.setBody(body);
        getLobbies.setSourceJobID(client.getNextJobID());

        if (filters != null) {
            filters.forEach(filter -> getLobbies.getBody().getFiltersList().add(filter.serialize().build()));
        }

        send(getLobbies, appId);
    }

    /**
     * Sends a request to join a lobby.
     * <p>
     * Returns nothing if the request could not be submitted i.e. not yet logged in.
     * Otherwise, an {@link JoinLobbyCallback}.
     *
     * @param appId        ID of app the lobby belongs to.
     * @param lobbySteamId The SteamID of the lobby that should be joined.
     */
    public void joinLobby(int appId, SteamID lobbySteamId) {
        var personaName = client.getHandler(SteamFriends.class).getPersonaName();

        if (personaName == null) {
            return;
        }

        var joinLobby = new ClientMsgProtobuf<SteammessagesClientserverMms.CMsgClientMMSJoinLobby.Builder>
                (SteammessagesClientserverMms.CMsgClientMMSJoinLobby.class, EMsg.ClientMMSJoinLobby);

        var body = joinLobby
                .getBody()
                .setAppId(appId)
                .setPersonaName(personaName)
                .setSteamIdLobby(lobbySteamId.convertToUInt64());

        joinLobby.setBody(body);
        joinLobby.setSourceJobID(client.getNextJobID());

        send(joinLobby, appId);
    }

    /**
     * Sends a request to leave a lobby.
     * <p>
     * Returns an {@link  LeaveLobbyCallback}.
     *
     * @param appId        ID of app the lobby belongs to.
     * @param lobbySteamId The SteamID of the lobby that should be left.
     */
    public void leaveLobby(int appId, SteamID lobbySteamId) {
        var leaveLobby = new ClientMsgProtobuf<SteammessagesClientserverMms.CMsgClientMMSLeaveLobby.Builder>
                (SteammessagesClientserverMms.CMsgClientMMSLeaveLobby.class, EMsg.ClientMMSLeaveLobby);

        var body = leaveLobby
                .getBody()
                .setAppId(appId)
                .setSteamIdLobby(lobbySteamId.convertToUInt64());

        leaveLobby.setBody(body);
        leaveLobby.setSourceJobID(client.getNextJobID());

        send(leaveLobby, appId);
    }

    /**
     * Sends a request to obtain a lobby's data.
     * <p>
     * Returns an {@link LobbyDataCallback}
     *
     * @param appId        The ID of app which we're attempting to obtain lobby data for.
     * @param lobbySteamId The SteamID of the lobby whose data is being requested.
     */
    public void getLobbyData(Integer appId, SteamID lobbySteamId) {
        var getLobbyData = new ClientMsgProtobuf<SteammessagesClientserverMms.CMsgClientMMSGetLobbyData.Builder>
                (SteammessagesClientserverMms.CMsgClientMMSGetLobbyData.class, EMsg.ClientMMSGetLobbyData);

        var body = getLobbyData
                .getBody()
                .setAppId(appId)
                .setSteamIdLobby(lobbySteamId.convertToUInt64());

        getLobbyData.setBody(body);
        getLobbyData.setSourceJobID(client.getNextJobID());

        send(getLobbyData, appId);
    }

    /**
     * Sends a lobby invite request.
     * NOTE: Steam provides no functionality to determine if the user was successfully invited.
     *
     * @param appId        The ID of app which owns the lobby we're inviting a user to.
     * @param lobbySteamId The SteamID of the lobby we're inviting a user to.
     * @param userSteamId  The SteamID of the user we're inviting.
     */
    public void inviteToLobby(int appId, SteamID lobbySteamId, SteamID userSteamId) {
        var lobbyData = new ClientMsgProtobuf<SteammessagesClientserverMms.CMsgClientMMSInviteToLobby.Builder>(
                SteammessagesClientserverMms.CMsgClientMMSInviteToLobby.class, EMsg.ClientMMSInviteToLobby);

        var body = lobbyData
                .getBody()
                .setAppId(appId)
                .setSteamIdLobby(lobbySteamId.convertToUInt64())
                .setSteamIdUserInvited(userSteamId.convertToUInt64());

        lobbyData.setBody(body);

        send(lobbyData, appId);
    }

    /**
     * Obtains a {@link Lobby}, by its SteamID, if the data is cached locally.
     * This method does not send a network request.
     *
     * @param appId        The ID of app which we're attempting to obtain a lobby for.
     * @param lobbySteamId The SteamID of the lobby that should be returned.
     * @return The {@link Lobby} corresponding with the specified app and lobby ID, if cached. Otherwise, <strong>null</strong>.
     */
    public Lobby getLobby(int appId, SteamID lobbySteamId) {
        return lobbyCache.getLobby(appId, lobbySteamId);
    }

    /**
     * Sends a matchmaking message for a specific app.
     *
     * @param msg   The matchmaking message to send.
     * @param appId The ID of the app this message pertains to.
     */
    public void send(ClientMsgProtobuf<?> msg, int appId) {
        if (msg == null) {
            throw new NullPointerException("msg is null");
        }

        msg.getProtoHeader().setRoutingAppid(appId);
        client.send(msg);
    }

    void clearLobbyCache() {
        lobbyCache.clear();
    }

    /**
     * Handles a client message. This should not be called directly.
     *
     * @param packetMsg The packet message that contains the data.
     */
    @Override
    public void handleMsg(IPacketMsg packetMsg) {
        if (packetMsg == null) {
            throw new IllegalArgumentException("packetMsg is null");
        }

        Consumer<IPacketMsg> dispatcher = dispatchMap.get(packetMsg.getMsgType());
        if (dispatcher != null) {
            dispatcher.accept(packetMsg);
        }
    }

    void handleCreateLobbyResponse(IPacketMsg packetMsg) {
        var createLobbyResponse = new ClientMsgProtobuf<SteammessagesClientserverMms.CMsgClientMMSCreateLobbyResponse.Builder>
                (SteammessagesClientserverMms.CMsgClientMMSCreateLobbyResponse.class, packetMsg);
        var body = createLobbyResponse.getBody();

        var request = lobbyManipulationRequests.remove(createLobbyResponse.getTargetJobID());
        if (body.getEresult() == EResult.OK.code() && request != null) {
            var createLobby = (SteammessagesClientserverMms.CMsgClientMMSCreateLobby) request;

            var members = new ArrayList<Lobby.Member>(1);
            members.add(new Lobby.Member(client.getSteamID(), createLobby.getPersonaNameOwner(), null));

            lobbyCache.cacheLobby(
                    createLobby.getAppId(),
                    new Lobby(
                            new SteamID(body.getSteamIdLobby()),
                            ELobbyType.from(createLobby.getLobbyType()),
                            createLobby.getLobbyFlags(),
                            client.getSteamID(),
                            Lobby.decodeMetadata(createLobby.getMetadata().toByteArray()),
                            createLobby.getMaxMembers(),
                            1,
                            members,
                            null,
                            null
                    )
            );
        }

        var callback = new CreateLobbyCallback(
                createLobbyResponse.getTargetJobID(),
                body.getAppId(),
                EResult.from(body.getEresult()),
                new SteamID(body.getSteamIdLobby())
        );
        client.postCallback(callback);
    }

    void handleSetLobbyDataResponse(IPacketMsg packetMsg) {
        var setLobbyDataResponse = new ClientMsgProtobuf<SteammessagesClientserverMms.CMsgClientMMSSetLobbyDataResponse.Builder>
                (SteammessagesClientserverMms.CMsgClientMMSSetLobbyDataResponse.class, packetMsg);
        var body = setLobbyDataResponse.getBody();

        var request = lobbyManipulationRequests.remove(setLobbyDataResponse.getTargetJobID());
        if (body.getEresult() == EResult.OK.code() && request != null) {
            var setLobbyData = (SteammessagesClientserverMms.CMsgClientMMSSetLobbyData) request;
            var lobby = lobbyCache.getLobby(setLobbyData.getAppId(), setLobbyData.getSteamIdLobby());

            if (lobby != null) {
                var metadata = Lobby.decodeMetadata(setLobbyData.getMetadata().toByteArray());

                if (setLobbyData.getSteamIdMember() == 0) {
                    lobbyCache.cacheLobby(
                            setLobbyData.getAppId(),
                            new Lobby(
                                    lobby.getSteamID(),
                                    ELobbyType.from(setLobbyData.getLobbyType()),
                                    setLobbyData.getLobbyFlags(),
                                    lobby.getOwnerSteamID(),
                                    metadata,
                                    setLobbyData.getMaxMembers(),
                                    lobby.getNumMembers(),
                                    lobby.getMembers(),
                                    lobby.getDistance(),
                                    lobby.getWeight()
                            )
                    );
                } else {
                    // I think this is right?
                    var members = new ArrayList<Lobby.Member>();
                    lobby.getMembers().forEach(m -> {
                        if (m.getSteamID().convertToUInt64() == setLobbyData.getSteamIdMember()) {
                            members.add(new Lobby.Member(m.getSteamID(), m.getPersonaName(), metadata));
                        } else {
                            members.add(m);
                        }
                    });

                    lobbyCache.updateLobbyMembers(setLobbyData.getAppId(), lobby, members);
                }
            }
        }

        var callback = new SetLobbyDataCallback(
                setLobbyDataResponse.getTargetJobID(),
                body.getAppId(),
                EResult.from(body.getEresult()),
                new SteamID(body.getSteamIdLobby())
        );
        client.postCallback(callback);
    }

    void handleSetLobbyOwnerResponse(IPacketMsg packetMsg) {
        var setLobbyOwnerResponse = new ClientMsgProtobuf<SteammessagesClientserverMms.CMsgClientMMSSetLobbyOwnerResponse.Builder>
                (SteammessagesClientserverMms.CMsgClientMMSSetLobbyOwnerResponse.class, packetMsg);
        var body = setLobbyOwnerResponse.getBody();

        var request = lobbyManipulationRequests.remove(setLobbyOwnerResponse.getTargetJobID());

        if (body.getEresult() == EResult.OK.code() && request != null) {
            var setLobbyOwner = (SteammessagesClientserverMms.CMsgClientMMSSetLobbyOwner) request;
            lobbyCache.updateLobbyOwner(body.getAppId(), body.getSteamIdLobby(), setLobbyOwner.getSteamIdNewOwner());
        }

        var callback = new SetLobbyOwnerCallback(
                setLobbyOwnerResponse.getTargetJobID(),
                body.getAppId(),
                EResult.from(body.getEresult()),
                new SteamID(body.getSteamIdLobby())
        );
        client.postCallback(callback);
    }

    void handleGetLobbyListResponse(IPacketMsg packetMsg) {
        var lobbyListResponse = new ClientMsgProtobuf<SteammessagesClientserverMms.CMsgClientMMSGetLobbyListResponse.Builder>
                (SteammessagesClientserverMms.CMsgClientMMSGetLobbyListResponse.class, packetMsg);
        var body = lobbyListResponse.getBody();

        var lobbyList = body.getLobbiesList().stream().map(lobby -> {
            var existingLobby = lobbyCache.getLobby(body.getAppId(), lobby.getSteamId());
            var members = existingLobby.getMembers();

            return new Lobby(
                    new SteamID(lobby.getSteamId()),
                    ELobbyType.from(lobby.getLobbyType()),
                    lobby.getLobbyFlags(),
                    existingLobby.getOwnerSteamID(),
                    Lobby.decodeMetadata(lobby.getMetadata().toByteArray()),
                    lobby.getMaxMembers(),
                    lobby.getNumMembers(),
                    members,
                    lobby.getDistance(),
                    lobby.getWeight()
            );
        }).collect(Collectors.toList());

        lobbyList.forEach(lobby -> lobbyCache.cacheLobby(body.getAppId(), lobby));

        var callback = new GetLobbyListCallback(
                lobbyListResponse.getTargetJobID(),
                body.getAppId(),
                EResult.from(body.getEresult()),
                lobbyList
        );
        client.postCallback(callback);
    }

    void handleJoinLobbyResponse(IPacketMsg packetMsg) {
        var joinLobbyResponse = new ClientMsgProtobuf<SteammessagesClientserverMms.CMsgClientMMSJoinLobbyResponse.Builder>
                (SteammessagesClientserverMms.CMsgClientMMSJoinLobbyResponse.class, packetMsg);
        var body = joinLobbyResponse.getBody();

        Lobby joinedLobby = null;

        if (body.hasSteamIdLobby()) {
            var members =
                    body.getMembersBuilderList().stream().map(member -> new Lobby.Member(
                            member.getSteamId(),
                            member.getPersonaName(),
                            Lobby.decodeMetadata(member.getMetadata().toByteArray())
                    )).collect(Collectors.toList());

            var cachedLobby = lobbyCache.getLobby(body.getAppId(), body.getSteamIdLobby());

            joinedLobby = new Lobby(
                    new SteamID(body.getSteamIdLobby()),
                    ELobbyType.from(body.getLobbyType()),
                    body.getLobbyFlags(),
                    new SteamID(body.getSteamIdOwner()),
                    Lobby.decodeMetadata(body.getMetadata().toByteArray()),
                    body.getMaxMembers(),
                    members.size(),
                    members,
                    cachedLobby.getDistance(),
                    cachedLobby.getWeight()
            );

            lobbyCache.cacheLobby(body.getAppId(), joinedLobby);
        }

        var callback = new JoinLobbyCallback(
                joinLobbyResponse.getTargetJobID(),
                body.getAppId(),
                EChatRoomEnterResponse.from(body.getChatRoomEnterResponse()),
                joinedLobby
        );
        client.postCallback(callback);
    }

    void handleLeaveLobbyResponse(IPacketMsg packetMsg) {
        var leaveLobbyResponse = new ClientMsgProtobuf<SteammessagesClientserverMms.CMsgClientMMSLeaveLobbyResponse.Builder>
                (SteammessagesClientserverMms.CMsgClientMMSLeaveLobbyResponse.class, packetMsg);
        var body = leaveLobbyResponse.getBody();

        if (body.getEresult() == EResult.OK.code()) {
            lobbyCache.clearLobbyMembers(body.getAppId(), body.getSteamIdLobby());
        }

        var callback = new LeaveLobbyCallback(
                leaveLobbyResponse.getTargetJobID(),
                body.getAppId(),
                body.getEresult(),
                body.getSteamIdLobby()
        );
        client.postCallback(callback);
    }

    void handleLobbyData(IPacketMsg packetMsg) {
        var lobbyDataResponse = new ClientMsgProtobuf<SteammessagesClientserverMms.CMsgClientMMSLobbyData.Builder>
                (SteammessagesClientserverMms.CMsgClientMMSLobbyData.class, packetMsg);

        var body = lobbyDataResponse.getBody();

        var cachedLobby = lobbyCache.getLobby(body.getAppId(), body.getSteamIdLobby());

        List<Lobby.Member> members;

        if (!body.getMembersList().isEmpty()) {
            members = body.getMembersList().stream().map(member ->
                    new Lobby.Member(
                            member.getSteamId(),
                            member.getPersonaName(),
                            Lobby.decodeMetadata(member.getMetadata().toByteArray()))
            ).collect(Collectors.toList());
        } else {
            members = cachedLobby.getMembers();
        }

        var updatedLobby = new Lobby(
                new SteamID(body.getSteamIdLobby()),
                ELobbyType.from(body.getLobbyType()),
                body.getLobbyFlags(),
                new SteamID(body.getSteamIdOwner()),
                Lobby.decodeMetadata(body.getMetadata().toByteArray()),
                body.getMaxMembers(),
                body.getNumMembers(),
                members,
                cachedLobby.getDistance(),
                cachedLobby.getWeight()
        );

        lobbyCache.cacheLobby(body.getAppId(), updatedLobby);

        var callback = new LobbyDataCallback(
                lobbyDataResponse.getTargetJobID(),
                body.getAppId(),
                updatedLobby
        );
        client.postCallback(callback);
    }

    void handleUserJoinedLobby(IPacketMsg packetMsg) {
        var userJoinedLobby = new ClientMsgProtobuf<SteammessagesClientserverMms.CMsgClientMMSUserJoinedLobby.Builder>
                (SteammessagesClientserverMms.CMsgClientMMSUserJoinedLobby.class, packetMsg);
        var body = userJoinedLobby.getBody();

        var lobby = lobbyCache.getLobby(body.getAppId(), body.getSteamIdLobby());

        if (lobby != null && lobby.getMembers().size() > 0) {
            var joiningMember = lobbyCache.addLobbyMember(body.getAppId(), lobby, body.getSteamIdUser(), body.getPersonaName());

            if (joiningMember != null) {
                var callback = new UserJoinedLobbyCallback(
                        body.getAppId(),
                        body.getSteamIdLobby(),
                        joiningMember
                );
                client.postCallback(callback);
            }
        }
    }

    void handleUserLeftLobby(IPacketMsg packetMsg) {
        var userLeftLobby = new ClientMsgProtobuf<SteammessagesClientserverMms.CMsgClientMMSUserLeftLobby.Builder>
                (SteammessagesClientserverMms.CMsgClientMMSUserLeftLobby.class, packetMsg);
        var body = userLeftLobby.getBody();

        var lobby = lobbyCache.getLobby(body.getAppId(), body.getSteamIdLobby());

        if (lobby != null && lobby.getMembers().size() > 0) {
            var leavingMember = lobbyCache.removeLobbyMember(body.getAppId(), lobby, body.getSteamIdUser());
            if (leavingMember == null) {
                return;
            }

            if (leavingMember.getSteamID() == client.getSteamID()) {
                lobbyCache.clearLobbyMembers(body.getAppId(), body.getSteamIdLobby());
            }

            var callback = new UserLeftLobbyCallback(body.getAppId(), body.getSteamIdLobby(), leavingMember);
            client.postCallback(callback);
        }
    }
}
