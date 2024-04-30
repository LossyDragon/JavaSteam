package `in`.dragonbra.javasteam.steam.handlers.steamworkshop

import `in`.dragonbra.javasteam.enums.EWorkshopFileAction

/**
 * Represents the details of an enumeration request used for the local user's files.
 *
 * @param appID Gets or sets the AppID of the workshop to enumerate.
 * @param startIndex Gets or sets the start index.
 * @param userAction Gets or sets the user action to filter by.
 *  This value is only used by [SteamWorkshop.enumeratePublishedFilesByUserAction].
 */
data class EnumerationUserDetails(
    var appID: Int = 0,
    var startIndex: Int = 0,
    var userAction: EWorkshopFileAction? = null,
)
