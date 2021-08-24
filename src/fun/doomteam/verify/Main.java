package fun.doomteam.verify;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.java.JavaPlugin;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public class Main extends JavaPlugin implements Listener {
	public static final String LOGIN_WEB_URL = "https://login.live.com/oauth20_authorize.srf?client_id=00000000402b5328&response_type=code&scope=service%3A%3Auser.auth.xboxlive.com%3A%3AMBI_SSL&redirect_uri=https%3A%2F%2Flogin.live.com%2Foauth20_desktop.srf";
	VerifyManager verifyManager;
	PlaceholderVerify placeholder;
	protected List<String> commandsMS = new ArrayList<>();
	protected List<String> commandsMojang = new ArrayList<>();
	protected boolean ignoreCase = false;
	protected boolean enableMS = true;
	protected boolean enableBugjump = true;
	protected int maxFailTime = 3;

	@Override
	public void onEnable() {
		this.saveDefaultConfig();
		this.reloadConfig();
		if (Util.init()) {
			(this.placeholder = new PlaceholderVerify(this)).register();
		}
		this.getServer().getPluginManager().registerEvents(this, this);
		this.getLogger().info("正版验证插件已启用");
	}

	@Override
	public void onDisable() {
		if (this.verifyManager != null)
			this.verifyManager.saveConfig();
		if (this.placeholder != null && this.placeholder.isRegistered())
			this.placeholder.unregister();

		this.getLogger().info("正版验证插件已卸载");
	}
	
    @EventHandler(priority=EventPriority.LOWEST)
    public void playerCommandShield(AsyncPlayerChatEvent event) {
    	String msg = event.getMessage().toLowerCase();
    	if(msg.startsWith("/doomsdayverify mojang")
    			|| msg.startsWith("/dv mojang")
    			|| msg.startsWith("/ms")) {
    		event.setMessage("/VERIFY_PROTECTED");
        }
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

	public String msg(String key) {
		if (!this.getConfig().contains("messages." + key)) {
			return "§4translate error: §cmessage." + key;
		}
		if (!this.getConfig().isString("messages." + key)) {
			return "§4wrong type: §cmessage." + key;
		}
		return ChatColor.translateAlternateColorCodes('&', this.getConfig().getString("messages." + key));
	}

	public String[] msgs(String key) {
		if (!this.getConfig().contains("messages." + key)) {
			return new String[] { "§4translate error: §cmessages." + key };
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

	@Override
	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
		if (sender instanceof Player) {
			Player player = (Player) sender;

			if (label.equalsIgnoreCase("ms")) {
				if (!enableMS) {
					player.sendMessage(msg("microsoft.disabled"));
					return true;
				}
				if(!player.hasPermission("doomsdayverify.microsoft")) {
					player.sendMessage(msg("no-permission"));
					return true;
				}
				if (args.length == 1) {
					String code = AuthUtil.getCodeFromUrl(args[0]);
					if (code == null) {
						player.sendMessage(msg("microsoft.invalid-link"));
						return true;
					}
					verifyManager.runMicrosoftLogin(player, code);
					return true;
				}
				linkMsgs(player, "microsoft.help", "<LINK>", LOGIN_WEB_URL, msg("microsoft.link-text"),
						msg("microsoft.link-hover"));
				return true;
			}
		}
		if (label.equalsIgnoreCase("doomsdayverify") || label.equalsIgnoreCase("dv")) {
			if (args.length >= 1) {
				if (args[0].equalsIgnoreCase("reload")) {
					this.saveDefaultConfig();
					this.reloadConfig();
					sender.sendMessage(msg("reload"));
					return true;
				}
				if (args[0].equalsIgnoreCase("time") && args.length == 3) {
					String player = args[1];
					int time = Util.strToInt(args[2], -1);
					if (Bukkit.getPlayer(player) == null) {
						sender.sendMessage(msg("no-player"));
						return true;
					}
					if (time <= 0) {
						sender.sendMessage(msg("no-positive-integer"));
						return true;
					}
					this.verifyManager.setPlayerFailTime(player, time);
					sender.sendMessage(
							msg("set-time").replace("%player%", player).replace("%time%", String.valueOf(time)));
					return true;
				}

				if (sender instanceof Player) {
					Player player = (Player) sender;
						if (args[0].equalsIgnoreCase("mojang")) {
							if (!enableBugjump) {
								player.sendMessage(msg("mojang.disabled"));
								return true;
							}
							if(!player.hasPermission("doomsdayverify.mojang")) {
								player.sendMessage(msg("no-permission"));
								return true;
							}
							if (args.length != 3) {
								player.sendMessage(msgs("mojang.help"));
								return true;
							}
							String email = args[1];
							String password = args[2];
							verifyManager.runMojangLogin(player, email, password);
							return true;
						
					}
				}
			}
			if(sender instanceof Player) {
				sender.sendMessage(msgs("help"));
			}
			if (sender.isOp()) {
				sender.sendMessage(msgs("help-op"));
			}
		}
		return true;
	}
}
