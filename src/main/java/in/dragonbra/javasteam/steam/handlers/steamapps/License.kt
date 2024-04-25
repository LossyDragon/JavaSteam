package `in`.dragonbra.javasteam.steam.handlers.steamapps

import `in`.dragonbra.javasteam.enums.ELicenseFlags
import `in`.dragonbra.javasteam.enums.ELicenseType
import `in`.dragonbra.javasteam.enums.EPaymentMethod
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserver.CMsgClientLicenseList
import java.util.*

/**
 * Represents a granted license (steam3 subscription) for one or more games.
 */
@Suppress("unused")
class License(license: CMsgClientLicenseList.License) {

    /**
     * Gets the package ID used to identify the license.
     * @return the package ID.
     */
    val packageID: Int = license.packageId

    /**
     * Gets the last change number for this license.
     * @return the last change number.
     */
    val lastChangeNumber: Int = license.changeNumber

    /**
     * Gets the time the license was created.
     * @return the time created.
     */
    val timeCreated: Date = Date(license.timeCreated * 1000L)

    /**
     * Gets the next process time for the license.
     * @return the next process time.
     */
    val timeNextProcess: Date = Date(license.timeNextProcess * 1000L)

    /**
     * Gets the minute limit of the license.
     * @return the minute limit.
     */
    val minuteLimit: Int = license.minuteLimit

    /**
     * Gets the minutes used of the license.
     * @return the minutes used.
     */
    val minutesUsed: Int = license.minutesUsed

    /**
     * Gets the payment method used when the license was created.
     * @return the payment method.
     */
    val paymentMethod: EPaymentMethod = EPaymentMethod.from(license.paymentMethod)

    /**
     * Gets the license flags.
     * @return the license flags.
     */
    val licenseFlags: EnumSet<ELicenseFlags> = ELicenseFlags.from(license.flags)

    /**
     * Gets the two-letter country code where the license was purchased.
     * @return the purchase country code.
     */
    val purchaseCode: String = license.purchaseCountryCode

    /**
     * Gets the type of the license.
     * @return the type of the license.
     */
    val licenseType: ELicenseType = ELicenseType.from(license.licenseType)

    /**
     * Gets the territory code of the license.
     * @return the territory code.
     */
    val territoryCode: Int = license.territoryCode

    /**
     * Gets the owner account id of the license.
     * @return the owned account id.
     */
    val ownerAccountID: Int = license.ownerId

    /**
     * Gets the PICS access token for this package.
     * @return the access token.
     */
    val accessToken: Long = license.accessToken

    /**
     * Gets the master package id.
     * @return the master package id.
     */
    val masterPackageID: Int = license.masterPackageId
}
