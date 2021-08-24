package fun.doomteam.verify;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import fun.doomteam.verify.AuthUtil.LoginResult;

public class VerifyManager {
	public class MicrosoftLoginTask implements Runnable {
		Player player;
		String code;
		BukkitTask task;

		MicrosoftLoginTask(Player player, String code) {
			this.player = player;
			this.code = code;
			this.task = Bukkit.getScheduler().runTaskAsynchronously(main, this);
			verifyMap.put(player.getName(), this);
		}

		public Player getPlayer() {
			return player;
		}

		public void run() {
			try {
				LoginResult result = AuthUtil.microsoft(code);
				if (result.isSuccess()) {
					if(main.ignoreCase ? result.getUsername().equalsIgnoreCase(player.getName()) : result.getUsername().equals(player.getName())) {
						putPlayer(player);
						Util.runCommands(main.commandsMS, player);
					}
					else {
						player.sendMessage(main.msg("microsoft.name-mismatched"));
						addPlayerFailTime(player);
					}
				} else {
					player.sendMessage(main.msg("error." + result.getInfo().name().toUpperCase()).replace("%extra%",
							result.getInfoString()));
					addPlayerFailTime(player);
				}
			} catch (Throwable t) {
				t.printStackTrace();
			}
			task.cancel();
			verifyMap.remove(player.getName());
		}
	}

	public class MojangLoginTask implements Runnable {
		Player player;
		String email;
		String password;
		BukkitTask task;

		MojangLoginTask(Player player, String email, String password) {
			this.player = player;
			this.email = email;
			this.password = password;
			this.task = Bukkit.getScheduler().runTaskAsynchronously(main, this);
			verifyMap.put(player.getName(), this);
		}

		public Player getPlayer() {
			return player;
		}

		public void run() {
			String pw = password;
			password = "******";
			try {
				LoginResult result = AuthUtil.mojang(email, pw);
				if (result.isSuccess()) {
					if(main.ignoreCase ? result.getUsername().equalsIgnoreCase(player.getName()) : result.getUsername().equals(player.getName())) {
						putPlayer(player);
						Util.runCommands(main.commandsMojang, player);
					}
					else {
						player.sendMessage(main.msg("mojang.name-mismatched"));
						addPlayerFailTime(player);
					}
				} else {
					player.sendMessage(main.msg("error." + result.getInfo().name().toUpperCase()).replace("%extra%",
							result.getInfoString()));
					addPlayerFailTime(player);
				}
			} catch (Throwable t) {
				t.printStackTrace();
			}
			task.cancel();
			verifyMap.remove(player.getName());
		}
	}

	Map<String, Runnable> verifyMap = new HashMap<>();
	File configFile;
	FileConfiguration config;
	Main main;

	public VerifyManager(Main main) {
		this.main = main;
		this.configFile = new File(main.getDataFolder(), "data.yml");
		this.reloadConfig();
	}

	public boolean isPlayerVerified(Player player) {
		return this.isPlayerVerified(player.getName());
	}

	public boolean isPlayerVerified(String player) {
		return config.getBoolean(player + ".verified" , false);
	}
	
	public int getPlayerFailTimes(Player player) {
		return this.getPlayerFailTimes(player.getName());
	}
	
	public int getPlayerFailTimes(String player) {
		return config.getInt(player + ".fail", 0);
	}

	public void addPlayerFailTime(Player player) {
		this.addPlayerFailTime(player.getName());
		int remainingTime = this.main.maxFailTime - getPlayerFailTimes(player);
		if(remainingTime == 0) {
			player.sendMessage(main.msg("time-run-out"));
		} else {
			player.sendMessage(main.msg("time-last").replace("%time%", String.valueOf(remainingTime)));
		}
	}
	
	public void addPlayerFailTime(String player) {
		int time = getPlayerFailTimes(player);
		if(time >= 0) time++;
		else time = 1;
		this.setPlayerFailTime(player, time);
	}

	public void setPlayerFailTime(Player player, int times) {
		this.setPlayerFailTime(player.getName(), times);
	}
	
	public void setPlayerFailTime(String player, int times) {
		config.set(player + ".fail", times);
		this.saveConfig();
	}

	public void putPlayer(Player player) {
		this.putPlayer(player.getName());
	}

	public void putPlayer(String player) {
		this.config.set(player + ".verified", true);
		this.saveConfig();
	}

	public void runMicrosoftLogin(Player player, String code) {
		if (this.isPlayerVerified(player)) {
			player.sendMessage(main.msg("verified"));
			return;
		}
		if (this.verifyMap.containsKey(player.getName())) {
			player.sendMessage(main.msg("verifing"));
			return;
		}
		if (this.getPlayerFailTimes(player) >= main.maxFailTime) {
			player.sendMessage(main.msg("no-time"));
			return;
		}
		player.sendMessage(main.msg("microsoft.start"));
		new MicrosoftLoginTask(player, code);
	}

	public void runMojangLogin(Player player, String email, String password) {
		if (this.isPlayerVerified(player)) {
			player.sendMessage(main.msg("verified"));
			return;
		}
		if (this.verifyMap.containsKey(player.getName())) {
			player.sendMessage(main.msg("verifing"));
			return;
		}
		if (this.getPlayerFailTimes(player) >= main.maxFailTime) {
			player.sendMessage(main.msg("no-time"));
			return;
		}
		player.sendMessage(main.msg("mojang.start"));
		new MojangLoginTask(player, email, password);
	}

	public void reloadConfig() {
		if (!configFile.exists()) {
			this.config = new YamlConfiguration();
			this.config.set("verified-players", new ArrayList<String>());
			this.saveConfig();
		} else {
			this.config = YamlConfiguration.loadConfiguration(configFile);
		}
		if (!this.config.contains("verified-players") || !this.config.isList("verified-players")) {
			this.config.set("verified-players", new ArrayList<String>());
		}
	}

	public void saveConfig() {
		try {
			if(this.config == null) this.config = new YamlConfiguration();
			this.config.save(configFile);
		} catch (Throwable t) {
			t.printStackTrace();
		}
	}
}
