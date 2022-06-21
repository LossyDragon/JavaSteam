package in.dragonbra.javasteam.steam.handlers.steammatchmaking;

import in.dragonbra.javasteam.types.SteamID;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

/**
 * @author lossy
 * @since 2022-06-12
 */
public class LobbyCache {

    private ConcurrentMap<Integer, ConcurrentHashMap<SteamID, Lobby>> lobbies = new ConcurrentHashMap<>();

    public Lobby getLobby(int appId, SteamID lobbySteamId) {
        return getAppLobbies(appId).getOrDefault(lobbySteamId, null);
    }

    public Lobby getLobby(int appId, long lobbySteamId) {
        return getAppLobbies(appId).getOrDefault(new SteamID(lobbySteamId), null);
    }

    public Lobby cacheLobby(int appId, Lobby lobby) {
        return getAppLobbies(appId).put(lobby.getSteamID(), lobby);
    }

    public Lobby.Member addLobbyMember(int appId, Lobby lobby, long memberId, String personaName) {
        return addLobbyMember(appId, lobby, new SteamID(memberId), personaName);
    }

    public Lobby.Member addLobbyMember(int appId, Lobby lobby, SteamID memberId, String personaName) {
        var existingMember = lobby.getMembers().stream().filter(member -> member.getSteamID().equals(memberId)).findAny().orElse(null);

        if (existingMember != null) {
            // Already in lobby.
            return null;
        }

        var addedMember = new Lobby.Member(memberId, personaName, null);

        var members = new ArrayList<Lobby.Member>(lobby.getMembers().size() + 1);
        members.addAll(lobby.getMembers());
        members.add(addedMember);

        updateLobbyMembers(appId, lobby, members);

        return addedMember;
    }

    public Lobby.Member removeLobbyMember(int appId, Lobby lobby, long memberId) {
        return removeLobbyMember(appId, lobby, new SteamID(memberId));
    }

    public Lobby.Member removeLobbyMember(int appId, Lobby lobby, SteamID memberId) {
        var removedMember = lobby.getMembers().stream().filter(member -> member.getSteamID().equals(memberId)).findAny().orElse(null);

        if (removedMember == null) {
            return null;
        }

        var members = lobby.getMembers().stream().filter(m -> !m.equals(removedMember)).collect(Collectors.toList());

        if (members.size() > 0) {
            updateLobbyMembers(appId, lobby, members);
        } else {
            // Steam deletes lobbies that contain no members.
            deleteLobby(appId, lobby.getSteamID());
        }

        return removedMember;
    }

    public void clearLobbyMembers(int appId, long lobbySteamId) {
        clearLobbyMembers(appId, new SteamID(lobbySteamId));
    }

    public void clearLobbyMembers(int appId, SteamID lobbySteamId) {
        var lobby = getLobby(appId, lobbySteamId);

        if (lobby != null) {
            updateLobbyMembers(appId, lobby, null, null);
        }
    }

    public void updateLobbyOwner(int appId, long lobbySteamId, long ownerSteamId) {
        updateLobbyOwner(appId, new SteamID(lobbySteamId), new SteamID(ownerSteamId));
    }

    public void updateLobbyOwner(int appId, SteamID lobbySteamId, SteamID ownerSteamId) {
        var lobby = getLobby(appId, lobbySteamId);

        if (lobby != null) {
            updateLobbyMembers(appId, lobby, ownerSteamId, lobby.getMembers());
        }
    }

    public void updateLobbyMembers(int appId, Lobby lobby, List<Lobby.Member> members) {
        updateLobbyMembers(appId, lobby, lobby.getOwnerSteamID(), members);
    }

    public void clear() {
        lobbies.clear();
    }

    void updateLobbyMembers(int appId, Lobby lobby, SteamID owner, List<Lobby.Member> members) {
        cacheLobby(appId, new Lobby(
                lobby.getSteamID(),
                lobby.getLobbyType(),
                lobby.getLobbyFlags(),
                owner,
                lobby.getMetadata(),
                lobby.getMaxMembers(),
                lobby.getNumMembers(),
                members,
                lobby.getDistance(),
                lobby.getWeight()
        ));
    }

    public ConcurrentMap<SteamID, Lobby> getAppLobbies(int appId) {
        return lobbies.putIfAbsent(appId, new ConcurrentHashMap<>());
    }

    public Lobby deleteLobby(int appId, SteamID lobbySteamId) {
        var appLobbies = lobbies.get(appId);

        if (appLobbies == null) {
            return null;
        }

        return appLobbies.remove(lobbySteamId);
    }
}
