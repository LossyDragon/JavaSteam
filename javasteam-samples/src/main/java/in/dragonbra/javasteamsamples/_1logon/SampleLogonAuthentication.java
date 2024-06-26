package in.dragonbra.javasteamsamples._1logon;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import in.dragonbra.javasteam.enums.EResult;
import in.dragonbra.javasteam.steam.authentication.*;
import in.dragonbra.javasteam.steam.handlers.steamunifiedmessages.SteamUnifiedMessages;
import in.dragonbra.javasteam.steam.handlers.steamuser.LogOnDetails;
import in.dragonbra.javasteam.steam.handlers.steamuser.SteamUser;
import in.dragonbra.javasteam.steam.handlers.steamuser.callback.LoggedOffCallback;
import in.dragonbra.javasteam.steam.handlers.steamuser.callback.LoggedOnCallback;
import in.dragonbra.javasteam.steam.steamclient.SteamClient;
import in.dragonbra.javasteam.steam.steamclient.callbackmgr.CallbackManager;
import in.dragonbra.javasteam.steam.steamclient.callbacks.ConnectedCallback;
import in.dragonbra.javasteam.steam.steamclient.callbacks.DisconnectedCallback;
import in.dragonbra.javasteam.util.log.DefaultLogListener;
import in.dragonbra.javasteam.util.log.LogManager;

import java.util.Base64;
import java.util.concurrent.CancellationException;

/**
 * @author lossy
 * @since 2023-03-19
 */
@SuppressWarnings("FieldCanBeLocal")
public class SampleLogonAuthentication implements Runnable {

    private SteamClient steamClient;

    private SteamUnifiedMessages unifiedMessages;

    private CallbackManager manager;

    private SteamUser steamUser;

    private boolean isRunning;

    private final String user;

    private final String pass;

    public SampleLogonAuthentication(String user, String pass) {
        this.user = user;
        this.pass = pass;
    }

    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Sample1: No username and password specified!");
            return;
        }

        LogManager.addListener(new DefaultLogListener());

        new SampleLogonAuthentication(args[0], args[1]).run();
    }

    @Override
    public void run() {

        // create our steamclient instance
        steamClient = new SteamClient();

        // create the callback manager which will route callbacks to function calls
        manager = new CallbackManager(steamClient);

        // get the steam unified messages handler, which is used for sending and receiving responses from the unified service api
        unifiedMessages = steamClient.getHandler(SteamUnifiedMessages.class);

        // get the steamuser handler, which is used for logging on after successfully connecting
        steamUser = steamClient.getHandler(SteamUser.class);

        // register a few callbacks we're interested in
        // these are registered upon creation to a callback manager, which will then route the callbacks
        // to the functions specified
        manager.subscribe(ConnectedCallback.class, this::onConnected);
        manager.subscribe(DisconnectedCallback.class, this::onDisconnected);

        manager.subscribe(LoggedOnCallback.class, this::onLoggedOn);
        manager.subscribe(LoggedOffCallback.class, this::onLoggedOff);

        isRunning = true;

        System.out.println("Connecting to steam...");

        // initiate the connection
        steamClient.connect();

        // create our callback handling loop
        while (isRunning) {
            // in order for the callbacks to get routed, they need to be handled by the manager
            manager.runWaitCallbacks(1000L);
        }
    }

    private void onConnected(ConnectedCallback callback) {
        System.out.println("Connected to Steam! Logging in " + user + "...");

        AuthSessionDetails authDetails = new AuthSessionDetails();
        authDetails.username = user;
        authDetails.password = pass;
        authDetails.persistentSession = false;
        authDetails.authenticator = new UserConsoleAuthenticator();

        try {
            // get the authentication handler, which used for authenticating with Steam
            SteamAuthentication auth = new SteamAuthentication(steamClient, unifiedMessages);

            CredentialsAuthSession authSession = auth.beginAuthSessionViaCredentials(authDetails);

            // Note: This is blocking, it would be up to you to make it non-blocking for Java.
            // Note: Kotlin uses should use ".pollingWaitForResult()" as its a suspending function.
            AuthPollResult pollResponse = authSession.pollingWaitForResultCompat().get();

            LogOnDetails details = new LogOnDetails();
            details.setUsername(pollResponse.getAccountName());
            details.setAccessToken(pollResponse.getRefreshToken());

            // Set LoginID to a non-zero value if you have another client connected using the same account,
            // the same private ip, and same public ip.
            details.setLoginID(149);

            steamUser.logOn(details);

            // This is not required, but it is possible to parse the JWT access token to see the scope and expiration date.
            // parseJsonWebToken(pollResponse.accessToken, "AccessToken");
            // parseJsonWebToken(pollResponse.refreshToken, "RefreshToken");
        } catch (Exception e) {
            System.err.println(e.getMessage());

            // List a couple of exceptions that could be important to handle.
            if (e instanceof AuthenticationException) {
                System.out.println("An Authentication error has occurred. " + e.getMessage());
            }

            if (e instanceof CancellationException) {
                System.out.println("An Cancellation exception was raised. Usually means a timeout occurred. " + e.getMessage());
            }
        }
    }

    private void onDisconnected(DisconnectedCallback callback) {
        System.out.println("Disconnected from Steam");

        isRunning = false;
    }

    private void onLoggedOn(LoggedOnCallback callback) {
        if (callback.getResult() != EResult.OK) {
            System.out.println("Unable to logon to Steam: " + callback.getResult() + " / " + callback.getExtendedResult());

            isRunning = false;
            return;
        }

        System.out.println("Successfully logged on!");

        // at this point, we'd be able to perform actions on Steam

        // for this sample we'll just log off
        steamUser.logOff();
    }

    private void onLoggedOff(LoggedOffCallback callback) {
        System.out.println("Logged off of Steam: " + callback.getResult());

        isRunning = false;
    }


    @SuppressWarnings("unused")
    private void parseJsonWebToken(String token, String name) {
        String[] tokenComponents = token.split("\\.");

        // Fix up base64url to normal base64
        String base64 = tokenComponents[1].replace('-', '+').replace('_', '/');

        if (base64.length() % 4 != 0) {
            base64 += new String(new char[4 - base64.length() % 4]).replace('\0', '=');
        }

        byte[] payloadBytes = Base64.getDecoder().decode(base64);

        // Payload can be parsed as JSON, and then fields such expiration date, scope, etc can be accessed
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        JsonElement payload = JsonParser.parseString(new String(payloadBytes));
        String formatted = gson.toJson(payload);

        // For brevity, we will simply output formatted json to console
        System.out.println(name + ": " + formatted);
        System.out.println();
    }

}
