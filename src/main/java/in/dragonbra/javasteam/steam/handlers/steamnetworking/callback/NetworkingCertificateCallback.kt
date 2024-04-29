package `in`.dragonbra.javasteam.steam.handlers.steamnetworking.callback

import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserver.CMsgClientNetworkingCertReply
import `in`.dragonbra.javasteam.steam.handlers.steamnetworking.SteamNetworking
import `in`.dragonbra.javasteam.steam.steamclient.callbackmgr.CallbackMsg
import `in`.dragonbra.javasteam.types.JobID

/**
 * This callback is received in response to calling [SteamNetworking.requestNetworkingCertificate].
 * This can be used to populate a CMsgSteamDatagramCertificateSigned for socket communication.
 */
@Suppress("unused", "MemberVisibilityCanBePrivate")
class NetworkingCertificateCallback(jobID: JobID, msg: CMsgClientNetworkingCertReply.Builder) : CallbackMsg() {

    /**
     * The certificate signed by the Steam CA. This contains a CMsgSteamDatagramCertificate with the supplied public key.
     * @return The certificate signed by the Steam CA.
     */
    val certificate: ByteArray = msg.cert.toByteArray()

    /**
     * The ID of the CA used to sign this certificate.
     * @return The ID of the CA used to sign this certificate.
     */
    val caKeyID: Long = msg.caKeyId

    /**
     * The signature used to verify [certificate].
     * @return The signature used to verify [certificate].
     */
    val caSignature: ByteArray = msg.caSignature.toByteArray()

    init {
        this.jobID = jobID
    }
}
