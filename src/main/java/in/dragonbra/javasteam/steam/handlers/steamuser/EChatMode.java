package in.dragonbra.javasteam.steam.handlers.steamuser;

/**
 * Represents the chat mode for logging into Steam.
 */
public enum EChatMode {
    /**
     * The default chat mode.
     */
    Default(0),

    /**
     * The chat mode for new Steam group chat.
     */
    NewSteamChat(2)

    ;

    private final int code;

    EChatMode(int code) {
        this.code = code;
    }

    public int code() {
        return this.code;
    }

    public static EChatMode from(int code) {
        for (EChatMode e : EChatMode.values()) {
            if (e.code == code) {
                return e;
            }
        }
        return null;
    }
}
