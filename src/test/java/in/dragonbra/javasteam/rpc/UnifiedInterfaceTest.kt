package `in`.dragonbra.javasteam.rpc

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.io.File

/**
 * This test class make sures there are a certain number of Unified classes/interfaces.
 *
 * Any updates to .proto files, either adding or removing services would need to reflect these tests.
 */
class UnifiedInterfaceTest {

    @Test
    fun testServiceCount() {
        assertServiceCount(SERVICE_PATH, knownServiceTypes)
    }

    @Test
    fun testKnownServices() {
        assertKnownServices(SERVICE_PATH, knownServiceTypes)
    }

    @Suppress("SameParameterValue")
    private fun assertServiceCount(path: String, knownTypes: Array<String>) {
        val interfaceDir = File(path)

        Assertions.assertTrue(
            interfaceDir.exists() && interfaceDir.isDirectory,
            "${interfaceDir.name} should exist to test"
        )

        val fileCount = interfaceDir.listFiles { file -> file.isFile }

        Assertions.assertNotNull(fileCount, "Couldn't count files")

        Assertions.assertTrue(
            knownTypes.count() == fileCount!!.size,
            "Interface count doesn't match known file types! Did something change in the .proto files?"
        )
    }

    @Suppress("SameParameterValue")
    private fun assertKnownServices(path: String, knownTypes: Array<String>) {
        for (filename in knownTypes) {
            val file = File(path, filename)
            Assertions.assertTrue(file.exists() && file.isFile, "File $filename should exist")
        }
    }

    private companion object {
        const val DIR_PATH = "build/generated/source/javasteam/main/java/in/dragonbra/javasteam/rpc/"
        const val SERVICE_PATH = "$DIR_PATH/service"

        /**
         * Any changes to then number of interfaces would need to reflect here. Otherwise, the test should fail.
         */
        val knownServiceTypes = arrayOf(
            // "AccountLinking.kt",
            "Authentication.kt",
            "AuthenticationSupport.kt",
            "Chat.kt",
            "ChatRoom.kt",
            "ChatRoomClient.kt",
            "ChatUsability.kt",
            "ChatUsabilityClient.kt",
            "ClanChatRooms.kt",
            "ClientMetrics.kt",
            "Cloud.kt",
            "CloudClient.kt",
            "CloudGaming.kt",
            "ContentServerDirectory.kt",
            "DepotContentDetection.kt",
            "EmbeddedClient.kt",
            "FamilyGroups.kt",
            "FamilyGroupsClient.kt",
            "FriendMessages.kt",
            "FriendMessagesClient.kt",
            "GameNotifications.kt",
            "GameNotificationsClient.kt",
            "Inventory.kt",
            "InventoryClient.kt",
            "Parental.kt",
            "ParentalClient.kt",
            "Player.kt",
            "PlayerClient.kt",
            "RemoteClient.kt",
            "RemoteClientSteamClient.kt",
            "Store.kt",
            "StoreBrowse.kt",
            "StoreClient.kt",
            "TwoFactor.kt",
            "UserAccount.kt",
            "PublishedFile.kt",
            "PublishedFileClient.kt",
        )
    }
}
