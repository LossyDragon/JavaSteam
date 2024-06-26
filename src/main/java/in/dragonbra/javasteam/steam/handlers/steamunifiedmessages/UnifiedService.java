package in.dragonbra.javasteam.steam.handlers.steamunifiedmessages;

import com.google.protobuf.GeneratedMessage;
import in.dragonbra.javasteam.steam.handlers.steamunifiedmessages.callback.ServiceMethodResponse;
import in.dragonbra.javasteam.types.AsyncJobSingle;

/**
 * @author Lossy
 * @since 2023-01-04
 */
@SuppressWarnings("unused")
public abstract class UnifiedService {

    // private static final Logger logger = LogManager.getLogger(UnifiedService.class);

    private final SteamUnifiedMessages steamUnifiedMessages;

    public UnifiedService(SteamUnifiedMessages steamUnifiedMessages) {
        this.steamUnifiedMessages = steamUnifiedMessages;
    }

    public String getClassName() {
        return this.getClass().getSimpleName();
    }

    /**
     * @param parentClassName The parent class name, ie: Player
     * @param methodName      The calling method name, ie: GetGameBadgeLevels
     * @return The name of the RPC endpoint as formatted ServiceName.RpcName. ie: Player.GetGameBadgeLevels#1
     */
    private static String getRpcEndpoint(String parentClassName, String methodName) {
        return String.format("%s.%s#%s", parentClassName, methodName, 1);
    }

    /**
     * Sends a message.
     * <p>
     * Results are returned in a
     * {@link in.dragonbra.javasteam.steam.handlers.steamunifiedmessages.callback.ServiceMethodResponse}.
     *
     * @param message    The message to send.
     * @param methodName The Target Job Name.
     * @return The JobID of the message. This can be used to find the appropriate
     * {@link in.dragonbra.javasteam.steam.handlers.steamunifiedmessages.callback.ServiceMethodResponse}.
     */
    public AsyncJobSingle<ServiceMethodResponse> sendMessage(GeneratedMessage message, String methodName) {
        String serviceName = getClassName();
        String rpcEndpoint = getRpcEndpoint(serviceName, methodName);

        return sendMessageOrNotification(rpcEndpoint, message, false);
    }

    /**
     * Sends a notification.
     *
     * @param message    The message to send.
     * @param methodName The Target Job Name.
     */
    public void sendNotification(GeneratedMessage message, String methodName) {
        String serviceName = getClassName();
        String rpcEndpoint = getRpcEndpoint(serviceName, methodName);

        sendMessageOrNotification(rpcEndpoint, message, true);
    }

    private AsyncJobSingle<ServiceMethodResponse> sendMessageOrNotification(String rpcName, GeneratedMessage message, Boolean isNotification) {

        if (isNotification) {
            steamUnifiedMessages.sendNotification(rpcName, message);
            return null;
        }

        return steamUnifiedMessages.sendMessage(rpcName, message);
    }
}
