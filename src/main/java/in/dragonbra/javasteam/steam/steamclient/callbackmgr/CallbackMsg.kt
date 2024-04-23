package `in`.dragonbra.javasteam.steam.steamclient.callbackmgr

import `in`.dragonbra.javasteam.types.JobID

/**
 * @author lngtr
 * @since 2018-02-22
 *
 * Represents the base object all callbacks are based off.
 *
 * @constructor Initializes a new instance of the [CallbackMsg] class.
 * @param jobID Gets or sets the job ID this callback refers to. If it is not a job callback, it will be [JobID.INVALID].
 */
open class CallbackMsg(override var jobID: JobID = JobID.INVALID) : ICallbackMsg
