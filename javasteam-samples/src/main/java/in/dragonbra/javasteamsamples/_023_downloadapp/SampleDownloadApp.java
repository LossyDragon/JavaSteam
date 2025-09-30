package in.dragonbra.javasteamsamples._023_downloadapp;

import in.dragonbra.javasteam.depotdownloader.ContentDownloader;
import in.dragonbra.javasteam.depotdownloader.data.AppItem;
import in.dragonbra.javasteam.depotdownloader.data.PubFileItem;
import in.dragonbra.javasteam.depotdownloader.data.UgcItem;
import in.dragonbra.javasteam.enums.EResult;
import in.dragonbra.javasteam.steam.authentication.AuthPollResult;
import in.dragonbra.javasteam.steam.authentication.AuthSessionDetails;
import in.dragonbra.javasteam.steam.authentication.AuthenticationException;
import in.dragonbra.javasteam.steam.authentication.UserConsoleAuthenticator;
import in.dragonbra.javasteam.steam.handlers.steamapps.License;
import in.dragonbra.javasteam.steam.handlers.steamapps.SteamApps;
import in.dragonbra.javasteam.steam.handlers.steamapps.callback.FreeLicenseCallback;
import in.dragonbra.javasteam.steam.handlers.steamapps.callback.LicenseListCallback;
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

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;


/**
 * @author Oxters
 * @since 2024-11-07
 * <p>
 * Sample 23: Download App
 * <p>
 * this sample introduces the usage of the content downloader API
 * <p>
 * content downloader lets you download an app from a Steam depot given
 * an app ID
 * <p>
 * in this case, this sample will demonstrate how to download the free game
 * called Rocky Mayhem
 */
@SuppressWarnings("FieldCanBeLocal")
public class SampleDownloadApp implements Runnable {

    private final int ROCKY_MAYHEM_APP_ID = 1303350;

    private final int ROCKY_MAYHEM_DEPOT_ID = 1303351;

    private SteamClient steamClient;

    private CallbackManager manager;

    private SteamUser steamUser;

    private SteamApps steamApps;

    private boolean isRunning;

    private final String user;

    private final String pass;

    private List<Closeable> subscriptions;

    private List<License> licenseList;

    public SampleDownloadApp(String user, String pass) {
        this.user = user;
        this.pass = pass;
    }

    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Sample 023: No username and password specified!");
            return;
        }

        LogManager.addListener(new DefaultLogListener());

        new SampleDownloadApp(args[0], args[1]).run();
    }

    @Override
    public void run() {
        // Most everything has been described in earlier samples.
        // Anything pertaining to this sample will be commented.

        steamClient = new SteamClient();

        manager = new CallbackManager(steamClient);

        steamUser = steamClient.getHandler(SteamUser.class);

        steamApps = steamClient.getHandler(SteamApps.class);

        subscriptions = new ArrayList<>();

        subscriptions.add(manager.subscribe(ConnectedCallback.class, this::onConnected));
        subscriptions.add(manager.subscribe(DisconnectedCallback.class, this::onDisconnected));
        subscriptions.add(manager.subscribe(LoggedOnCallback.class, this::onLoggedOn));
        subscriptions.add(manager.subscribe(LoggedOffCallback.class, this::onLoggedOff));
        subscriptions.add(manager.subscribe(LicenseListCallback.class, this::onLicenseList));
        subscriptions.add(manager.subscribe(FreeLicenseCallback.class, this::onFreeLicense));

        isRunning = true;

        System.out.println("Connecting to steam...");

        steamClient.connect();

        while (isRunning) {
            manager.runWaitCallbacks(1000L);
        }

        for (var subscription : subscriptions) {
            try {
                subscription.close();
            } catch (IOException e) {
                System.out.println("Couldn't close a callback.");
            }
        }
    }

    private void onConnected(ConnectedCallback callback) {
        System.out.println("Connected to Steam! Logging in " + user + "...");

        AuthSessionDetails authDetails = new AuthSessionDetails();
        authDetails.username = user;
        authDetails.password = pass;
        authDetails.deviceFriendlyName = "JavaSteam - Sample 023";
        authDetails.persistentSession = true;

        authDetails.authenticator = new UserConsoleAuthenticator();

        try {
            var path = Paths.get("refreshtoken.txt");

            String accountName;
            String refreshToken;
            if (!Files.exists(path)) {
                System.out.println("No existing refresh token found. Beginning Authentication");

                var authSession = steamClient.getAuthentication().beginAuthSessionViaCredentials(authDetails).get();

                AuthPollResult pollResponse = authSession.pollingWaitForResult().get();

                accountName = pollResponse.getAccountName();
                refreshToken = pollResponse.getRefreshToken();

                // Save out refresh token for automatic login on next sample run.
                Files.writeString(path, pollResponse.getRefreshToken());
            } else {
                System.out.println("Existing refresh token found");
                var token = Files.readString(path);

                accountName = user;
                refreshToken = token;
            }

            LogOnDetails details = new LogOnDetails();
            details.setUsername(accountName);
            details.setAccessToken(refreshToken);
            details.setShouldRememberPassword(true);

            details.setLoginID(149);

            System.out.println("Logging in...");

            steamUser.logOn(details);
        } catch (Exception e) {
            if (e instanceof AuthenticationException) {
                System.err.println("An Authentication error has occurred. " + e.getMessage());
            } else if (e instanceof CancellationException) {
                System.err.println("An Cancellation exception was raised. Usually means a timeout occurred. " + e.getMessage());
            } else {
                System.err.println("An error occurred:" + e.getMessage());
            }

            steamUser.logOff();
        }
    }

    private void onDisconnected(DisconnectedCallback callback) {
        System.out.println("Disconnected from Steam");

        if (callback.isUserInitiated()) {
            isRunning = false;
        } else {
            try {
                Thread.sleep(2000L);
                steamClient.connect();
            } catch (InterruptedException e) {
                System.err.println("An Interrupted exception occurred. " + e.getMessage());
            }
        }
    }

    private void onLoggedOn(LoggedOnCallback callback) {
        if (callback.getResult() != EResult.OK) {
            if (callback.getResult() == EResult.AccountLogonDenied) {
                System.out.println("Unable to logon to Steam: This account is SteamGuard protected.");

                isRunning = false;
                return;
            }

            System.out.println("Unable to logon to Steam: " + callback.getResult());

            isRunning = false;
            return;

        }

        System.out.println("Successfully logged on!");

        // now that we are logged in, we can request a free license for Rocky Mayhem
        steamApps.requestFreeLicense(ROCKY_MAYHEM_APP_ID);
    }

    private void onLicenseList(LicenseListCallback callback) {
        if (callback.getResult() != EResult.OK) {
            System.out.println("Failed to obtain licenses the account owns.");
            steamClient.disconnect();
            return;
        }

        licenseList = callback.getLicenseList();

        System.out.println("Got " + licenseList.size() + " licenses from account!");
    }

    @SuppressWarnings("ExtractMethodRecommender")
    private void onFreeLicense(FreeLicenseCallback callback) {
        if (callback.getResult() != EResult.OK) {
            System.out.println("Failed to get a free license for Rocky Mayhem");
            steamClient.disconnect();
            return;
        }

        // Initiate the DepotDownloader, it is a Closable so it can be cleaned up when no longer used.
        // You will need to subscribe to LicenseListCallback to obtain your app licenses.
        try (var depotDownloader = new ContentDownloader(steamClient, licenseList, "steamapps", true)) {

            // An app id is required at minimum for all item types.
            var pubItem = new PubFileItem(
                    /* appId */ 0,
                    /* pubfile */ 0,
                    /* downloadManifestOnly */ false
            ); // TODO find actual pub item

            var ugcItem = new UgcItem(
                    /* appId */0,
                    /* ugcId */ 0,
                    /* downloadManifestOnly */ false
            ); // TODO find actual ugc item

            var appItem = new AppItem(
                    /* appId */ 204360,
                    /* branch */ "public",
                    /* branchPassword */ "",
                    /* downloadAllPlatforms */ false,
                    /* os */ "windows",
                    /* downloadAllArchs */ false,
                    /* osArch */ "64",
                    /* downloadAllLanguages */ false,
                    /* language */ "english",
                    /* lowViolence */ false,
                    /* depot */ List.of(),
                    /* manifest */ List.of(),
                    /* downloadManifestOnly */ false
            );

            var appItem2 = new AppItem(225840);
            var appItem3 = new AppItem(3527290);
            var appItem4 = new AppItem(ROCKY_MAYHEM_APP_ID);

            var downloadList = List.of(pubItem, ugcItem, appItem);

            // Add specified games to the queue. Add, Remove, Move, and general array manipulation methods are available.
            // depotDownloader.addAll(downloadList); // TODO
            depotDownloader.addAll(List.of(appItem, appItem2, appItem3, appItem4));

            // Start downloading your items. Array manipulation is now disabled. You can still add to the list.
            var success = depotDownloader.start().get(); // Future<Boolean>

            if (success) {
                System.out.println("Download completed successfully");
            }
        } catch (ExecutionException | InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            steamUser.logOff();

            System.out.println("Done Downloading");
        }
    }

    private void onLoggedOff(LoggedOffCallback callback) {
        System.out.println("Logged off of Steam: " + callback.getResult());

        isRunning = false;
    }
}
