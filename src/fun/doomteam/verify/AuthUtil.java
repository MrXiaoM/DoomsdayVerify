package fun.doomteam.verify;

import static org.jackhuang.hmcl.util.Lang.mapOf;
import static org.jackhuang.hmcl.util.Pair.pair;

import java.net.HttpURLConnection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import org.jackhuang.hmcl.util.gson.UUIDTypeAdapter;
import org.jackhuang.hmcl.util.gson.ValidationTypeAdapterFactory;
import org.jackhuang.hmcl.util.io.HttpRequest;
import org.jackhuang.hmcl.util.io.NetworkUtils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Minecraft account verification util
 * 
 * Most code from https://github.com/huanghongxun/HMCL
 * org.jackhuang.hmcl.auth.microsoft.MicrosoftService
 * org.jackhuang.hmcl.auth.yggdrasil.YggdrasilService
 * 
 * just CV and modify it
 * 
 * @author MrXiaoM
 */
public class AuthUtil {
	private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(UUID.class, UUIDTypeAdapter.INSTANCE)
            .registerTypeAdapterFactory(ValidationTypeAdapterFactory.INSTANCE)
            .create();
	public static class LoginResult{
		private boolean success;
		private String info;
		private String uuid;
		private String username;
		private LoginResult(boolean success, String info) {
			this(success, info, "", "");
		}
		private LoginResult(boolean success, String info, String uuid, String username) {
			this.success = success;
			this.info = info;
			this.uuid = uuid;
			this.username = username;
		}
		public boolean isSuccess() {
			return success;
		}
		public String getInfoString() {
			return info;
		}
		public Info getInfo() {
			return Info.getInfo(info);
		}
		public String getUuid() {
			return uuid;
		}
		public String getUsername() {
			return username;
		}
		public static enum Info {
			INVALID_CODE("'code' parameter is not valid"),
			UHS_MISMATCHED("uhs mismatched"),
			CLIENT_TOKEN_MISMATCHED("client token mismatched"),
			NO_PROFILE("profile not found"),
			NO_SELECTED("no seleted profile"),
			UNKNOWN("");
			String containsValue;
			Info(String containsValue) {
				this.containsValue = containsValue;
			}

			public static Info getInfo(String value) {
				for(Info info : Info.values()) {
					if(value.contains(info.containsValue)) {
						return info;
					}
				}
				return Info.UNKNOWN;
			}
			public static Info getInfoIgnoreCase(String value) {
				for(Info info : Info.values()) {
					if(value.toLowerCase().contains(info.containsValue.toLowerCase())) {
						return info;
					}
				}
				return Info.UNKNOWN;
			}
		}
	}
	@Nullable
	public static String getCodeFromUrl(String str) {
		String url = str;
		if(url.contains("?")) url = url.substring(url.indexOf("?") + 1);
		else return null;
		if(url.contains("code=")) url = url.substring(url.indexOf("code=") + 5);
		else return null;
		if(url.contains("&")) url = url.substring(0, url.indexOf("&"));
		return url;
	}
	private static LoginResult success(String uuid, String username) {
		return new LoginResult(true, "", uuid, username);
	}
	
	private static LoginResult fail(String info) {
		return new LoginResult(false, info);
	}
	
	public static LoginResult microsoft(String code) {
		try {
			String responseText = HttpRequest.POST("https://login.live.com/oauth20_token.srf")
	            .form(mapOf(
	            		pair("client_id", "00000000402b5328"), 
	            		pair("code", code),
	                    pair("grant_type", "authorization_code"),
	                    pair("redirect_uri", "https://login.live.com/oauth20_desktop.srf"), 
	                    pair("scope", "service::user.auth.xboxlive.com::MBI_SSL")
	            )).getString();
			JsonParser parser = new JsonParser();
			JsonObject json = parser.parse(responseText).getAsJsonObject();
			if(json.has("error")) {
				return fail(json.get("error_descrption").getAsString());
			}
			String liveAccessToken = json.get("access_token").getAsString();
			// String liveRefreshToken = json.getString("refresh_token");

			String xboxUserResponseText = HttpRequest.POST("https://user.auth.xboxlive.com/user/authenticate")
	                .json(mapOf(
	                        pair("Properties", mapOf(
	                                pair("AuthMethod", "RPS"),
	                                pair("SiteName", "user.auth.xboxlive.com"),
	                                pair("RpsTicket", liveAccessToken)
	                        )),
	                        pair("RelyingParty", "http://auth.xboxlive.com"),
	                        pair("TokenType", "JWT")
	                )).getString();
			json = parser.parse(xboxUserResponseText).getAsJsonObject();
			String xboxUserToken = json.get("Token").getAsString();
			String uhs = json.get("DisplayClaims").getAsJsonObject()
					.get("xui").getAsJsonArray()
					.get(0).getAsJsonObject()
					.get("uhs").getAsString();
			
			String minecraftXstsResponseText = HttpRequest.POST("https://xsts.auth.xboxlive.com/xsts/authorize")
					.json(mapOf(
							pair("Properties", mapOf(
									pair("SandboxId", "RETAIL"),
	                                pair("UserTokens", Collections.singletonList(xboxUserToken))
	                        )),
							pair("RelyingParty", "rp://api.minecraftservices.com/"), 
							pair("TokenType", "JWT")
					)).getString();
			json = parser.parse(minecraftXstsResponseText).getAsJsonObject();
			String mcXstsUhs = json.get("DisplayClaims").getAsJsonObject()
					.get("xui").getAsJsonArray()
					.get(0).getAsJsonObject()
					.get("uhs").getAsString();
			if(!uhs.equals(mcXstsUhs)) {
				return fail("uhs mismatched");
			}
			String mcXstsToken = json.get("Token").getAsString();

			String xboxXstsResponseText = HttpRequest.POST("https://xsts.auth.xboxlive.com/xsts/authorize")
	                .json(mapOf(
	                        pair("Properties", mapOf(
	                        		pair("SandboxId", "RETAIL"),
	                                pair("UserTokens", Collections.singletonList(xboxUserToken))
	                        )),
	                        pair("RelyingParty", "http://xboxlive.com"), 
	                        pair("TokenType", "JWT")
	                )).getString();
			json = parser.parse(xboxXstsResponseText).getAsJsonObject();
			String xboxXstsUhs = json.get("DisplayClaims").getAsJsonObject()
					.get("xui").getAsJsonArray()
					.get(0).getAsJsonObject()
					.get("uhs").getAsString();
			if(!uhs.equals(xboxXstsUhs)) {
				return fail("uhs mismatched");
			}
			String xboxXstsToken = json.get("Token").getAsString();
			
	        HttpRequest.GET("https://profile.xboxlive.com/users/me/profile/settings",
	                pair("settings", "GameDisplayName,AppDisplayName,AppDisplayPicRaw,GameDisplayPicRaw,"
	                        + "PublicGamerpic,ShowUserAsAvatar,Gamerscore,Gamertag,ModernGamertag,ModernGamertagSuffix,"
	                        + "UniqueModernGamertag,AccountTier,TenureLevel,XboxOneRep,"
	                        + "PreferredColor,Location,Bio,Watermarks," + "RealName,RealNameOverride,IsQuarantined"))
	                .contentType("application/json").accept("application/json")
	                .authorization(String.format("XBL3.0 x=%s;%s", uhs, xboxXstsToken)).header("x-xbl-contract-version", "3")
	                .getString();

	        String minecraftResponseText = HttpRequest.POST("https://api.minecraftservices.com/authentication/login_with_xbox")
	                .json(mapOf(pair("identityToken", "XBL3.0 x=" + uhs + ";" + mcXstsToken)))
	                .accept("application/json").getString();
	        json = parser.parse(minecraftResponseText).getAsJsonObject();
	        String tokenType = json.get("token_type").getAsString();
	        String accessToken = json.get("access_token").getAsString();
	        
	        HttpURLConnection conn = HttpRequest.GET("https://api.minecraftservices.com/minecraft/profile")
	                .contentType("application/json").authorization(String.format("%s %s", tokenType, accessToken))
	                .createConnection();
	        int responseCode = conn.getResponseCode();
	        if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
	            fail("profile not found");
	        }
	        String profileResponseText = NetworkUtils.readData(conn);
	        json = parser.parse(profileResponseText).getAsJsonObject();
	        String uuid = json.get("id").getAsString();
	        String username = json.get("name").getAsString();
	        // no need to get accessToken for this
	        return success(uuid, username);
		} catch(Exception e) {
			e.printStackTrace();
			return fail(e.getClass().getName() + ": " + (e.getMessage() == null ? "" : e.getMessage()));
		}
	}
	
	public static LoginResult yggdrasil(String email, String password, String authUrl) {
		try {
			String clientToken = UUID.randomUUID().toString().replace("-", "");
			Map<String, Object> request = new HashMap<>();
        	request.put("agent", mapOf(
                pair("name", "Minecraft"),
                pair("version", 1)
        	));
        	request.put("username", email);
        	request.put("password", password);
        	request.put("clientToken", clientToken);
        	request.put("requestUser", true);
        	
			String response = NetworkUtils.doPost(NetworkUtils.toURL(authUrl), GSON.toJson(request), "application/json");
			JsonParser parser = new JsonParser();
			JsonObject json = parser.parse(response).getAsJsonObject();
			if(json.has("error")) {
				return fail(json.get("error").getAsString()  + ", " + json.get("errorMessage").getAsString() + ", " + json.get("cause").getAsString());
			}
			if(!json.get("clientToken").getAsString().equals(clientToken)) {
				return fail("client token mismatched");
			}
			JsonArray availableProfiles = json.get("availableProfiles").getAsJsonArray();
			if(availableProfiles == null || availableProfiles.size() == 0) {
				return fail("profile not found");
			}
			if(!json.has("selectedProfile")) {
				return fail("no seleted profile");
			}
			JsonObject selectedProfile = json.get("selectedProfile").getAsJsonObject();
			String uuid = selectedProfile.get("id").getAsString();
			String username = selectedProfile.get("name").getAsString();
			return success(uuid, username);
		}catch(Exception e) {
			e.printStackTrace();
			return fail(e.getClass().getName() + ": " + (e.getMessage() == null ? "" : e.getMessage()));
		}
	}
	
	public static LoginResult mojang(String email, String password) {
		return yggdrasil(email, password, "https://authserver.mojang.com/authenticate");
	}
}
