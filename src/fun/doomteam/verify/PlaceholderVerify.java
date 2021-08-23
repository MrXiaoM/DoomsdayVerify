package fun.doomteam.verify;

import org.bukkit.OfflinePlayer;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.md_5.bungee.api.ChatColor;

public class PlaceholderVerify extends PlaceholderExpansion {
	private final Main main;

	public PlaceholderVerify(Main main) {
		this.main = main;
	}

	public String getAuthor() {
		return "MrXiaoM";
	}

	public String getIdentifier() {
		return main.getDescription().getName().toLowerCase();
	}

	public String onRequest(OfflinePlayer player, String identifier) {
		if (main.getVerifyManager() == null)
			return identifier;
		if (identifier.equalsIgnoreCase("verified")) {
			return main.getVerifyManager().isPlayerVerified(player.getName()) ? "yes" : "no";
		}
		if (identifier.toLowerCase().startsWith("verified_")) {
			String[] args = identifier.split("_");
			if(args.length == 3) {
				return ChatColor.translateAlternateColorCodes('&', main.getVerifyManager().isPlayerVerified(player.getName()) ? args[1] : args[2]);
			}
		}
		if (identifier.equalsIgnoreCase("fail_time")) {
			return String.valueOf(main.getVerifyManager().getPlayerFailTimes(player.getName()));
		}
		return identifier;
	}

	public String getVersion() {
		return main.getDescription().getVersion();
	}

	public boolean persist() {
		return true;
	}

	public boolean canRegister() {
		return true;
	}
}
