package fun.doomteam.verify;

import static java.net.HttpURLConnection.HTTP_NOT_FOUND;
import static org.jackhuang.hmcl.util.Lang.mapOf;
import static org.jackhuang.hmcl.util.Pair.pair;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.Objects;

import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.gson.TolerableValidationException;
import org.jackhuang.hmcl.util.gson.UUIDTypeAdapter;
import org.jackhuang.hmcl.util.gson.Validation;
import org.jackhuang.hmcl.util.gson.ValidationTypeAdapterFactory;
import org.jackhuang.hmcl.util.io.HttpRequest;
import org.jackhuang.hmcl.util.io.NetworkUtils;
import org.jackhuang.hmcl.util.io.ResponseCodeException;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.annotations.SerializedName;

/**
 * Minecraft account verification util
 * 
 * Most code from https://github.com/huanghongxun/HMCL
 * org.jackhuang.hmcl.auth.OAuth
 * org.jackhuang.hmcl.auth.microsoft.MicrosoftService
 * org.jackhuang.hmcl.auth.yggdrasil.YggdrasilService
 * 
 * just CV and modify it
 * 
 * @author MrXiaoM
 */
public class AuthUtil {
	private static final Gson GSON = new GsonBuilder().registerTypeAdapter(UUID.class, UUIDTypeAdapter.INSTANCE)
			.registerTypeAdapterFactory(ValidationTypeAdapterFactory.INSTANCE).create();

	public static class LoginResult {
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
			MOJANG_INVALID_CREDENTIALS("Invalid username or password"), MS_NO_ACCOUNT("not have mc service"),
			PROFILE_ERROR("profile error: "), NO_PROFILE("profile not found"), NO_SELECTED("no seleted profile"),
			UNKNOWN("");

			String containsValue;

			Info(String containsValue) {
				this.containsValue = containsValue;
			}

			public static Info getInfo(String value) {
				for (Info info : Info.values()) {
					if (value.contains(info.containsValue)) {
						return info;
					}
				}
				return Info.UNKNOWN;
			}

			public static Info getInfoIgnoreCase(String value) {
				for (Info info : Info.values()) {
					if (value.toLowerCase().contains(info.containsValue.toLowerCase())) {
						return info;
					}
				}
				return Info.UNKNOWN;
			}
		}
	}

	private static LoginResult success(String uuid, String username) {
		return new LoginResult(true, "", uuid, username);
	}

	private static LoginResult fail(String info) {
		return new LoginResult(false, info);
	}

	public static String requestClientSecret() {
		// return HttpRequest.GET("").getString();
		return "";
	}

	public static LoginResult microsoft(String code) {
		try {
			// TODO client_secret 每2年手动更新一次，在 ms 分支的 CLIENT_SECRET 文件里。只要域名还在，插件就能用
			// 我的条件不允许我订阅 Azure 免费账户，做不了联合凭据，只能以此作为临时解决方法
			String client_secret = HttpRequest.GET("https://verify.doomteam.fun/CLIENT_SECRET").getString();
			
			String responseText = HttpRequest.POST("https://login.live.com/oauth20_token.srf")
	                .form(pair("client_id", "73600c5b-7c5b-41f0-90ab-19a953d79b92"), pair("code", code),
	                        pair("grant_type", "authorization_code"), pair("client_secret", client_secret),
	                        pair("redirect_uri", "https://verify.doomteam.fun/result"), pair("scope", "XboxLive.signin offline_access"))
	                .ignoreHttpCode()
	                .getString();
			JsonParser parser = new JsonParser();
			JsonObject json = parser.parse(responseText).getAsJsonObject();
			if (json.has("error")) {
				return fail(json.toString());
			}
			String liveAccessToken = json.get("access_token").getAsString();
	        // Authenticate with XBox Live
	        XBoxLiveAuthenticationResponse xboxResponse = HttpRequest
	                .POST("https://user.auth.xboxlive.com/user/authenticate")
	                .json(mapOf(
	                        pair("Properties",
	                                mapOf(pair("AuthMethod", "RPS"), pair("SiteName", "user.auth.xboxlive.com"),
	                                        pair("RpsTicket", "d=" + liveAccessToken))),
	                        pair("RelyingParty", "http://auth.xboxlive.com"), pair("TokenType", "JWT")))
	                .accept("application/json").getJson(XBoxLiveAuthenticationResponse.class);

	        String uhs = getUhs(xboxResponse, null);

	        // Authenticate Minecraft with XSTS
	        XBoxLiveAuthenticationResponse minecraftXstsResponse = HttpRequest
	                .POST("https://xsts.auth.xboxlive.com/xsts/authorize")
	                .json(mapOf(
	                        pair("Properties",
	                                mapOf(pair("SandboxId", "RETAIL"),
	                                        pair("UserTokens", Collections.singletonList(xboxResponse.token)))),
	                        pair("RelyingParty", "rp://api.minecraftservices.com/"), pair("TokenType", "JWT")))
	                .ignoreHttpErrorCode(401)
	                .getJson(XBoxLiveAuthenticationResponse.class);

	        getUhs(minecraftXstsResponse, uhs);

	        // Authenticate with Minecraft
	        MinecraftLoginWithXBoxResponse minecraftResponse = HttpRequest
	                .POST("https://api.minecraftservices.com/authentication/login_with_xbox")
	                .json(mapOf(pair("identityToken", "XBL3.0 x=" + uhs + ";" + minecraftXstsResponse.token)))
	                .accept("application/json").getJson(MinecraftLoginWithXBoxResponse.class);

	        // long notAfter = minecraftResponse.expiresIn * 1000L + System.currentTimeMillis();

	        // Get Minecraft Account UUID
	        MinecraftProfileResponse profileResponse = getMinecraftProfile(minecraftResponse.tokenType, minecraftResponse.accessToken);
	        if (profileResponse.error != null) {
	            throw new RemoteAuthenticationException(profileResponse.error, profileResponse.errorMessage, profileResponse.developerMessage);
	        }
	        
			// no need to get accessToken for this
			return success(profileResponse.id.toString(), profileResponse.name);
		} catch(Throwable t) {
			t.printStackTrace();
			return fail(t.getClass().getName() + ": " + (t.getMessage() == null ? "" : t.getMessage()));
		}
	}

	public static LoginResult yggdrasil(String email, String password, String authUrl) {
		try {
			String clientToken = UUID.randomUUID().toString().replace("-", "");
			Map<String, Object> request = new HashMap<>();
			request.put("agent", mapOf(pair("name", "Minecraft"), pair("version", 1)));
			request.put("username", email);
			request.put("password", password);
			request.put("clientToken", clientToken);
			request.put("requestUser", true);

			String response = NetworkUtils.doPost(NetworkUtils.toURL(authUrl), GSON.toJson(request),
					"application/json");
			JsonParser parser = new JsonParser();
			JsonObject json = parser.parse(response).getAsJsonObject();
			if (json.has("error")) {
				return fail(json.get("error").getAsString() + ", " + json.get("errorMessage").getAsString()
						+ (json.has("cause") ? ", " + json.get("cause").getAsString() : ""));
			}
			if (!json.get("clientToken").getAsString().equals(clientToken)) {
				return fail("client token mismatched");
			}
			JsonArray availableProfiles = json.get("availableProfiles").getAsJsonArray();
			if (availableProfiles == null || availableProfiles.size() == 0) {
				return fail("profile not found");
			}
			if (!json.has("selectedProfile")) {
				return fail("no seleted profile");
			}
			JsonObject selectedProfile = json.get("selectedProfile").getAsJsonObject();
			String uuid = selectedProfile.get("id").getAsString();
			String username = selectedProfile.get("name").getAsString();
			return success(uuid, username);
		} catch (Throwable t) {
			t.printStackTrace();
			return fail(t.getClass().getName() + ": " + (t.getMessage() == null ? "" : t.getMessage()));
		}
	}

	public static LoginResult mojang(String email, String password) {
		return yggdrasil(email, password, "https://authserver.mojang.com/authenticate");
	}

    private static MinecraftProfileResponse getMinecraftProfile(String tokenType, String accessToken)
            throws IOException, AuthenticationException {
        HttpURLConnection conn = HttpRequest.GET("https://api.minecraftservices.com/minecraft/profile")
                .authorization(tokenType, accessToken)
                .createConnection();
        int responseCode = conn.getResponseCode();
        if (responseCode == HTTP_NOT_FOUND) {
            throw new NoMinecraftJavaEditionProfileException();
        } else if (responseCode != 200) {
            throw new ResponseCodeException(new URL("https://api.minecraftservices.com/minecraft/profile"), responseCode);
        }

        String result = NetworkUtils.readData(conn);
        return JsonUtils.fromNonNullJson(result, MinecraftProfileResponse.class);
    }

    private static String getUhs(XBoxLiveAuthenticationResponse response, String existingUhs) throws AuthenticationException {
        if (response.errorCode != 0) {
            throw new XboxAuthorizationException(response.errorCode, response.redirectUrl);
        }

        if (response.displayClaims == null || response.displayClaims.xui == null || response.displayClaims.xui.size() == 0 || !response.displayClaims.xui.get(0).containsKey("uhs")) {
            Logger.getGlobal().warning("Unrecognized xbox authorization response " + GSON.toJson(response));
            throw new NoXuiException();
        }

        String uhs = (String) response.displayClaims.xui.get(0).get("uhs");
        if (existingUhs != null) {
            if (!Objects.equals(uhs, existingUhs)) {
                throw new ServerResponseMalformedException("uhs mismatched");
            }
        }
        return uhs;
    }
    /**
    *
    * @author huangyuhui
    */
   public static class AuthenticationException extends Exception {
       /**
		 * 
		 */
		private static final long serialVersionUID = 1L;

	public AuthenticationException() {
       }

       public AuthenticationException(String message) {
           super(message);
       }

       public AuthenticationException(String message, Throwable cause) {
           super(message, cause);
       }

       public AuthenticationException(Throwable cause) {
           super(cause);
       }
   }
   public static class RemoteAuthenticationException extends AuthenticationException {

	    /**
	 * 
	 */
	private static final long serialVersionUID = 1L;
		private final String name;
	    private final String message;
	    private final String cause;

	    public RemoteAuthenticationException(String name, String message, String cause) {
	        super(buildMessage(name, message, cause));
	        this.name = name;
	        this.message = message;
	        this.cause = cause;
	    }

	    public String getRemoteName() {
	        return name;
	    }

	    public String getRemoteMessage() {
	        return message;
	    }

	    public String getRemoteCause() {
	        return cause;
	    }

	    private static String buildMessage(String name, String message, String cause) {
	        StringBuilder builder = new StringBuilder(name);
	        if (message != null)
	            builder.append(": ").append(message);

	        if (cause != null)
	            builder.append(": ").append(cause);

	        return builder.toString();
	    }
	}
    public static class XboxAuthorizationException extends AuthenticationException {
        /**
		 * 
		 */
		private static final long serialVersionUID = 1L;
		private final long errorCode;
        private final String redirect;

        public XboxAuthorizationException(long errorCode, String redirect) {
            this.errorCode = errorCode;
            this.redirect = redirect;
        }

        public long getErrorCode() {
            return errorCode;
        }

        public String getRedirect() {
            return redirect;
        }

        public static final long MISSING_XBOX_ACCOUNT = 2148916233L;
        public static final long COUNTRY_UNAVAILABLE = 2148916235L;
        public static final long ADD_FAMILY = 2148916238L;
    }
    
    public static class ServerResponseMalformedException extends AuthenticationException {
        /**
		 * 
		 */
		private static final long serialVersionUID = 1L;

		public ServerResponseMalformedException() {
        }

        public ServerResponseMalformedException(String message) {
            super(message);
        }

        public ServerResponseMalformedException(String message, Throwable cause) {
            super(message, cause);
        }

        public ServerResponseMalformedException(Throwable cause) {
            super(cause);
        }
    }
    
    public static class NoMinecraftJavaEditionProfileException extends AuthenticationException {

		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;
    }

    public static class NoXuiException extends AuthenticationException {

		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;
    }

    private static class XBoxLiveAuthenticationResponseDisplayClaims {
        List<Map<Object, Object>> xui;
    }

    private static class MicrosoftErrorResponse {
        @SerializedName("XErr")
        long errorCode;

        @SerializedName("Message")
        String message;

        @SerializedName("Redirect")
        String redirectUrl;
    }

    /**
     * Success Response: { "IssueInstant":"2020-12-07T19:52:08.4463796Z",
     * "NotAfter":"2020-12-21T19:52:08.4463796Z", "Token":"token", "DisplayClaims":{
     * "xui":[ { "uhs":"userhash" } ] } }
     * <p>
     * Error response: { "Identity":"0", "XErr":2148916238, "Message":"",
     * "Redirect":"https://start.ui.xboxlive.com/AddChildToFamily" }
     * <p>
     * XErr Candidates: 2148916233 = missing XBox account 2148916238 = child account
     * not linked to a family
     */
    private static class XBoxLiveAuthenticationResponse extends MicrosoftErrorResponse {
        @SerializedName("IssueInstant")
        String issueInstant;

        @SerializedName("NotAfter")
        String notAfter;

        @SerializedName("Token")
        String token;

        @SerializedName("DisplayClaims")
        XBoxLiveAuthenticationResponseDisplayClaims displayClaims;
    }

    private static class MinecraftLoginWithXBoxResponse {
        @SerializedName("username")
        String username;

        @SerializedName("roles")
        List<String> roles;

        @SerializedName("access_token")
        String accessToken;

        @SerializedName("token_type")
        String tokenType;

        @SerializedName("expires_in")
        int expiresIn;
    }

    public static class MinecraftProfileResponseSkin implements Validation {
        public String id;
        public String state;
        public String url;
        public String variant; // CLASSIC, SLIM
        public String alias;

        @Override
        public void validate() throws JsonParseException, TolerableValidationException {
            Validation.requireNonNull(id, "id cannot be null");
            Validation.requireNonNull(state, "state cannot be null");
            Validation.requireNonNull(url, "url cannot be null");
            Validation.requireNonNull(variant, "variant cannot be null");
        }
    }

    public static class MinecraftProfileResponseCape {

    }

    public static class MinecraftProfileResponse extends MinecraftErrorResponse implements Validation {
        @SerializedName("id")
        UUID id;
        @SerializedName("name")
        String name;
        @SerializedName("skins")
        List<MinecraftProfileResponseSkin> skins;
        @SerializedName("capes")
        List<MinecraftProfileResponseCape> capes;

        @Override
        public void validate() throws JsonParseException, TolerableValidationException {
            Validation.requireNonNull(id, "id cannot be null");
            Validation.requireNonNull(name, "name cannot be null");
            Validation.requireNonNull(skins, "skins cannot be null");
            Validation.requireNonNull(capes, "capes cannot be null");
        }
    }

    private static class MinecraftErrorResponse {
        public String path;
        public String errorType;
        public String error;
        public String errorMessage;
        public String developerMessage;
    }
}
