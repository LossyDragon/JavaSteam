package `in`.dragonbra.javasteam.depotdownloader

import `in`.dragonbra.javasteam.types.DepotManifest

class ProtoManifest {

    companion object {
        @JvmStatic
        fun loadFromFile(filename: String): Pair<ProtoManifest?, ByteArray?> {
            return null to null // TODO
        }

        @JvmStatic
        fun saveToFile(filename: String): ByteArray {
            return byteArrayOf() // TODO
        }

        @JvmStatic
        fun convertToSteamManifest(depotId: UInt): DepotManifest {
            return DepotManifest() // TODO
        }
    }
}
