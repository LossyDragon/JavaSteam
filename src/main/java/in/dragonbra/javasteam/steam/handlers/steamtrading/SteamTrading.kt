package `in`.dragonbra.javasteam.steam.handlers.steamtrading

import `in`.dragonbra.javasteam.base.ClientMsgProtobuf
import `in`.dragonbra.javasteam.base.IPacketMsg
import `in`.dragonbra.javasteam.enums.EMsg
import `in`.dragonbra.javasteam.handlers.ClientMsgHandler
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserver2.CMsgTrading_CancelTradeRequest
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserver2.CMsgTrading_InitiateTradeRequest
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserver2.CMsgTrading_InitiateTradeResponse
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserver2.CMsgTrading_StartSession
import `in`.dragonbra.javasteam.steam.handlers.steamtrading.callback.SessionStartCallback
import `in`.dragonbra.javasteam.steam.handlers.steamtrading.callback.TradeProposedCallback
import `in`.dragonbra.javasteam.steam.handlers.steamtrading.callback.TradeResultCallback
import `in`.dragonbra.javasteam.types.SteamID
import `in`.dragonbra.javasteam.util.compat.Consumer
import java.util.*

/**
 * This handler is used for initializing Steam trades with other clients.
 */
@Suppress("unused")
class SteamTrading : ClientMsgHandler() {

    private var dispatchMap: EnumMap<EMsg, Consumer<IPacketMsg>> = EnumMap(EMsg::class.java)

    init {
        dispatchMap[EMsg.EconTrading_InitiateTradeProposed] = Consumer(::handleTradeProposed)
        dispatchMap[EMsg.EconTrading_InitiateTradeResult] = Consumer(::handleTradeResult)
        dispatchMap[EMsg.EconTrading_StartSession] = Consumer(::handleStartSession)
    }

    /**
     * Proposes a trade to another client.
     * @param user The client to trade.
     */
    fun trade(user: SteamID) {
        ClientMsgProtobuf<CMsgTrading_InitiateTradeRequest.Builder>(
            CMsgTrading_InitiateTradeRequest::class.java,
            EMsg.EconTrading_InitiateTradeRequest
        ).apply {
            body.setOtherSteamid(user.convertToUInt64())
        }.also(client::send)
    }

    /**
     * Responds to a trade proposal.
     * @param tradeId     The trade id of the  received proposal.
     * @param acceptTrade if set to **true**, the trade will be accepted.
     */
    fun respondToTrade(tradeId: Int, acceptTrade: Boolean) {
        ClientMsgProtobuf<CMsgTrading_InitiateTradeResponse.Builder>(
            CMsgTrading_InitiateTradeResponse::class.java,
            EMsg.EconTrading_InitiateTradeResponse
        ).apply {
            body.setTradeRequestId(tradeId)
            body.setResponse(if (acceptTrade) 1 else 0)
        }.also(client::send)
    }

    /**
     * Cancels an already sent trade proposal.
     * @param user The user.
     */
    fun cancelTrade(user: SteamID) {
        ClientMsgProtobuf<CMsgTrading_CancelTradeRequest.Builder>(
            CMsgTrading_CancelTradeRequest::class.java,
            EMsg.EconTrading_CancelTradeRequest
        ).apply {
            body.setOtherSteamid(user.convertToUInt64())
        }.also(client::send)
    }

    override fun handleMsg(packetMsg: IPacketMsg) {
        dispatchMap[packetMsg.msgType]?.accept(packetMsg)
    }

    private fun handleTradeProposed(packetMsg: IPacketMsg) {
        ClientMsgProtobuf<CMsgTrading_InitiateTradeRequest.Builder>(
            CMsgTrading_InitiateTradeRequest::class.java,
            packetMsg
        ).also { tradeProp ->
            TradeProposedCallback(tradeProp.body).also(client::postCallback)
        }
    }

    private fun handleTradeResult(packetMsg: IPacketMsg) {
        ClientMsgProtobuf<CMsgTrading_InitiateTradeResponse.Builder>(
            CMsgTrading_InitiateTradeResponse::class.java,
            packetMsg
        ).also { tradeResult ->
            TradeResultCallback(tradeResult.body).also(client::postCallback)
        }
    }

    private fun handleStartSession(packetMsg: IPacketMsg) {
        ClientMsgProtobuf<CMsgTrading_StartSession.Builder>(
            CMsgTrading_StartSession::class.java,
            packetMsg
        ).also { startSess ->
            SessionStartCallback(startSess.body).also(client::postCallback)
        }
    }
}
