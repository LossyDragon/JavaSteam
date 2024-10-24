package `in`.dragonbra.javasteam.steam.steamclient.callbackmgr

import `in`.dragonbra.javasteam.types.JobID

/**
 * Represents the base object all callbacks are based off.
 *
 * @constructor Initializes a new instance of the [CallbackMsg] class.
 *
 * @author lngtr
 * @since 2018-02-22
 */
abstract class CallbackMsg {

    /**
     * The [JobID] that this callback is associated with. If there is no job associated,
     * then this will be [JobID.INVALID]
     */
    var jobID: JobID = JobID.INVALID
}
