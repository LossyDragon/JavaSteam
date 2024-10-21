package in.dragonbra.javasteam.steam.discovery;

import in.dragonbra.javasteam.TestBase;
import in.dragonbra.javasteam.networking.steam3.ProtocolTypes;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;


public class FileServerListProviderTest extends TestBase {

    @TempDir
    Path folder;

    @Test
    public void testSaveAndRead() throws IOException {
        final Path tempFile = Files.createFile(folder.resolve("FileServerListProvider.txt"));

        Assertions.assertTrue(tempFile.toFile().exists());
        Assertions.assertEquals("FileServerListProvider.txt", tempFile.getFileName().toString());

        FileServerListProvider provider = new FileServerListProvider(tempFile);

        List<ServerRecord> serverRecords = new ArrayList<>();

        serverRecords.add(ServerRecord.createServer("162.254.197.42", 27017, ProtocolTypes.TCP));
        serverRecords.add(ServerRecord.createServer("162.254.197.42", 27018, ProtocolTypes.TCP));
        serverRecords.add(ServerRecord.createServer("162.254.197.42", 27017, ProtocolTypes.UDP));
        serverRecords.add(ServerRecord.createServer("CM02-FRA.cm.steampowered.com", 27017, ProtocolTypes.WEB_SOCKET));

        provider.updateServerList(serverRecords);

        List<ServerRecord> loadedList = provider.fetchServerList();

        Assertions.assertEquals(serverRecords, loadedList);
    }

    @Test
    public void testMissingFile() throws IOException {
        final Path tempFile = Files.createFile(folder.resolve("FileServerListProvider.txt"));

        Assertions.assertTrue(tempFile.toFile().exists());
        Assertions.assertEquals("FileServerListProvider.txt", tempFile.getFileName().toString());

        FileServerListProvider provider = new FileServerListProvider(tempFile);

        List<ServerRecord> serverRecords = provider.fetchServerList();

        Assertions.assertTrue(serverRecords.isEmpty());
    }

    @Test
    public void testLastServerListRefresh() throws IOException {
        final Path tempFile = Files.createFile(folder.resolve("FileServerListProvider.txt"));

        FileServerListProvider provider = new FileServerListProvider(tempFile);

        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Instant lastRefresh = provider.getLastServerListRefresh().truncatedTo(ChronoUnit.SECONDS);
        Assertions.assertEquals(now, lastRefresh);
    }
}
