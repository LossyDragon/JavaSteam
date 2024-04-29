package `in`.dragonbra.javasteam.steam.handlers.steamscreenshots

import `in`.dragonbra.javasteam.enums.EUCMFilePrivacyState
import `in`.dragonbra.javasteam.types.GameID
import java.util.*

/**
 * Represents the details required to add a screenshot
 *
 * @constructor Initializes a new instance of the [ScreenshotDetails] class.
 */
class ScreenshotDetails {

    /**
     * Gets or sets the Steam game ID this screenshot belongs to
     * @return the Steam game ID this screenshot belongs to
     */
    var gameID: GameID? = null

    /**
     * Gets or sets the UFS image filepath.
     * @return the UFS image filepath.
     */
    var ufsImageFilePath: String? = null

    /**
     * Gets or sets the UFS thumbnail filepath.
     * @return the UFS thumbnail filepath.
     */
    var usfThumbnailFilePath: String? = null

    /**
     * Gets or sets the screenshot caption
     * @return the screenshot caption
     */
    var caption: String? = null

    /**
     * Gets or sets the screenshot privacy
     * @return the screenshot privacy
     */
    var privacy: EUCMFilePrivacyState? = null

    /**
     * Gets or sets the screenshot width
     * @return the screenshot width
     */
    var width: Int = 0

    /**
     * Gets or sets the screenshot height
     * @return the screenshot height
     */
    var height: Int = 0

    /**
     * Gets or sets the creation time
     * @return the creation time
     */
    var creationTime: Date? = null

    /**
     * Gets or sets whether the screenshot contains spoilers
     * @return whether the screenshot contains spoilers
     */
    var isContainsSpoilers: Boolean = false
}
