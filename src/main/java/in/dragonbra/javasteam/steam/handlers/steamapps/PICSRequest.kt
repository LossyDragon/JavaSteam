package `in`.dragonbra.javasteam.steam.handlers.steamapps

/**
 * Represents a PICS request used for [SteamApps.picsGetProductInfo]
 *
 * @constructor Instantiate a PICS product info request for a given app or package id and an access token.
 * @param id App or package ID.
 * @param accessToken PICS access token.
 */
class PICSRequest @JvmOverloads constructor(var id: Int = 0, val accessToken: Long = 0L)
