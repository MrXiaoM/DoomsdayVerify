package fun.doomteam.verify;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import com.comphenix.protocol.ProtocolManager;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public class Main extends JavaPlugin implements Listener {

	public static final String LOGIN_WEB_URL = "https://verify.doomteam.fun/";
	protected static Main INSTANCE;
	VerifyManager verifyManager;
	PlaceholderVerify placeholder;
	ProtocolManager pm;
	Commands cmds;
	protected boolean hasProtocolLib = false;
	protected List<String> commandsMS = new ArrayList<>();
	protected List<String> commandsMojang = new ArrayList<>();
	protected boolean ignoreCase = false;
	protected boolean enableMS = true;
	protected boolean enableBugjump = true;
	protected boolean enableInGame = true;
	protected int maxFailTime = 3;
	private YamlConfiguration defaultConfig = new YamlConfiguration();

	@Override
	public void onEnable() {
		this.saveDefaultConfig();
		this.reloadConfig();
		InputStream is = this.getResource("config.yml");
		if (is != null) {
			this.defaultConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(is));
		}
		this.cmds = new Commands(this);
		if (Util.init()) {
			(this.placeholder = new PlaceholderVerify(this)).register();
		}
		this.getServer().getPluginManager().registerEvents(this, this);
		INSTANCE = this;
	}

	@Override
	public void onDisable() {
		if (this.verifyManager != null)
			this.verifyManager.saveConfig();
		if (this.placeholder != null && this.placeholder.isRegistered())
			this.placeholder.unregister();
		if (this.cmds != null)
			this.cmds.onDisable();
	}

	public void reloadConfig() {
		super.reloadConfig();
		FileConfiguration config = this.getConfig();
		this.ignoreCase = config.getBoolean("ignore-case", false);
		this.commandsMS = config.getStringList("commands-ms");
		this.commandsMojang = config.getStringList("commands-mojang");
		this.enableBugjump = config.getBoolean("enable-mojang", true);
		this.enableMS = config.getBoolean("enable-microsoft", true);
		this.maxFailTime = config.getInt("max-verify-times", 3);
		if (verifyManager == null)
			this.verifyManager = new VerifyManager(this);
		else
			this.verifyManager.reloadConfig();
	}

	public VerifyManager getVerifyManager() {
		return this.verifyManager;
	}

	public String msgDefault(String key) {
		if (!this.defaultConfig.contains("messages." + key)) {
			return "§4translate error: §cmessage." + key;
		}
		if (!this.defaultConfig.isString("messages." + key)) {
			return "§4wrong type: §cmessage." + key;
		}
		return ChatColor.translateAlternateColorCodes('&', this.defaultConfig.getString("messages." + key));
	}

	public String msg(String key) {
		if (!this.getConfig().contains("messages." + key)) {
			return this.msgDefault(key);
		}
		if (!this.getConfig().isString("messages." + key)) {
			return "§4wrong type: §cmessage." + key;
		}
		return ChatColor.translateAlternateColorCodes('&', this.getConfig().getString("messages." + key));
	}

	public String[] msgsDefault(String key) {
		if (!this.defaultConfig.contains("messages." + key)) {
			return new String[] { "§4translate error: §cmessages." + key };
		}
		if (!this.defaultConfig.isList("messages." + key)) {
			return new String[] { "§4wrong type: §cmessages." + key };
		}
		List<String> list = this.defaultConfig.getStringList("messages." + key);
		String[] array = new String[list.size()];
		for (int i = 0; i < list.size(); i++) {
			array[i] = ChatColor.translateAlternateColorCodes('&', list.get(i));
		}
		return array;
	}

	public String[] msgs(String key) {
		if (!this.getConfig().contains("messages." + key)) {
			return this.msgsDefault(key);
		}
		if (!this.getConfig().isList("messages." + key)) {
			return new String[] { "§4wrong type: §cmessages." + key };
		}
		List<String> list = this.getConfig().getStringList("messages." + key);
		String[] array = new String[list.size()];
		for (int i = 0; i < list.size(); i++) {
			array[i] = ChatColor.translateAlternateColorCodes('&', list.get(i));
		}
		return array;
	}

	public void linkMsgs(Player player, String key, String split, String link, String linkText, String hoverText) {
		if (!this.getConfig().contains("messages." + key)) {
			player.sendMessage("§4translate error: §cmessages." + key);
			return;
		}
		if (!this.getConfig().isList("messages." + key)) {
			player.sendMessage("§4wrong type: §cmessages." + key);
			return;
		}
		List<String> list = this.getConfig().getStringList("messages." + key);
		for (String s : list) {
			if (s.contains(split)) {
				String[] a = ChatColor.translateAlternateColorCodes('&', s).split(split);
				JsonArray array = new JsonArray();
				// meaningless
				if (a.length > 0) {
					array.add(getText(a[0]));
				}
				JsonObject json = new JsonObject();
				json.addProperty("text", linkText);
				json.add("clickEvent", getEvent("open_url", link));
				json.add("hoverEvent", getEvent("show_text", hoverText));
				array.add(json);
				if (a.length > 1) {
					array.add(getText(a[1]));
				}
				Util.sendTellraw(player, array.toString());
				continue;
			}
			player.sendMessage(ChatColor.translateAlternateColorCodes('&', s));
		}
	}

	private JsonObject getEvent(String action, String value) {
		JsonObject json = new JsonObject();
		json.addProperty("action", action);
		json.addProperty("value", value);
		return json;
	}

	private JsonObject getText(String text) {
		JsonObject json = new JsonObject();
		json.addProperty("text", text);
		return json;
	}

	public Main getInstance() {
		return INSTANCE;
	}
}
