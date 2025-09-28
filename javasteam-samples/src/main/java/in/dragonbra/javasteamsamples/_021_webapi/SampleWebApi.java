package in.dragonbra.javasteamsamples._021_webapi;

import in.dragonbra.javasteam.steam.steamclient.SteamClient;
import in.dragonbra.javasteam.steam.webapi.WebAPI;
import in.dragonbra.javasteam.types.KeyValue;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

//
// Sample 6: WebAPI
//
// this sample will give an example of how the WebAPI utilities can be used to
// interact with the Steam Web APIs
//
// the Steam Web APIs are structured as a set of "interfaces" with methods,
// similar to classes in OO languages.
// as such, the API for interacting with the WebAPI follows a similar methodology

/**
 * @author lngtr
 * @since 2021-10-11
 */
@SuppressWarnings("FieldCanBeLocal")
public class SampleWebApi {

    public SampleWebApi() {
    }

    public static void main(String[] args) {

        // create our steamclient instance
        SteamClient steamClient = new SteamClient();

        WebAPI api = steamClient.getConfiguration().getWebAPI("ISteamNews");

        try {
            Map<String, String> arguments = new HashMap<>();
            arguments.put("appid", "440");

            KeyValue result = api.call("GetNewsForApp", 2, arguments);

            System.out.println(result.toFormattedString());
        } catch (IOException e) {
            System.err.println(e.getMessage());
        }
    }
}
