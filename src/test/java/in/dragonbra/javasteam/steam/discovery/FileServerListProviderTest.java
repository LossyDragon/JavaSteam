package in.dragonbra.javasteam.steam.discovery;

import in.dragonbra.javasteam.TestBase;
import in.dragonbra.javasteam.networking.steam3.ProtocolTypes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FileServerListProviderTest extends TestBase {

    @TempDir
    Path folder;

    @Test
    public void testSaveAndRead() {
        File file = folder.resolve("provider").toFile();
        FileServerListProvider provider = new FileServerListProvider(file);

        List<ServerRecord> serverRecords = new ArrayList<>();

        serverRecords.add(ServerRecord.createServer("162.254.197.42", 27017, ProtocolTypes.TCP));
        serverRecords.add(ServerRecord.createServer("162.254.197.42", 27018, ProtocolTypes.TCP));
        serverRecords.add(ServerRecord.createServer("162.254.197.42", 27017, ProtocolTypes.UDP));
        serverRecords.add(ServerRecord.createServer("CM02-FRA.cm.steampowered.com", 27017, ProtocolTypes.WEB_SOCKET));

        provider.updateServerList(serverRecords);

        List<ServerRecord> loadedList = provider.fetchServerList();

        assertEquals(loadedList, serverRecords);
    }

    @Test
    public void testMissingFile() {
        File file = folder.resolve("provider.txt").toFile();
        FileServerListProvider provider = new FileServerListProvider(file);

        List<ServerRecord> serverRecords = provider.fetchServerList();

        assertTrue(serverRecords.isEmpty());
    }
}