package fun.doomteam.verify;

import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.google.common.collect.Lists;

public class Commands extends PacketAdapter implements CommandExecutor {
	public static class ProtocolLibSupport extends PacketAdapter {
		Commands parent;

		public ProtocolLibSupport(Commands parent) {
			super(parent.main, ListenerPriority.NORMAL, PacketType.Play.Client.CHAT);
			this.parent = parent;
		}

		@Override
		public void onPacketReceiving(PacketEvent event) {
			if (event.getPacketType() != PacketType.Play.Client.CHAT)
				return;
			String s = event.getPacket().getStrings().read(0);
			if (!s.startsWith("/"))
				return;
			String[] args = s.contains(" ") ? s.substring(1).split(" ") : new String[] { s.substring(1) };
			Player player = event.getPlayer();
			if (args[0].equalsIgnoreCase(parent.main.getConfig().getString("command-ms-prefix", "ms"))) {
				event.setCancelled(true);
				if (!parent.main.enableMS) {
					player.sendMessage(parent.main.msg("microsoft.disabled"));
					return;
				}
				if (!player.hasPermission("doomsdayverify.microsoft")) {
					player.sendMessage(parent.main.msg("no-permission"));
					return;
				}
				if (parent.main.getVerifyManager().isPlayerVerified(player)) {
					player.sendMessage(parent.main.msg("verified"));
					return;
				}
				if (parent.main.getVerifyManager().isPlayerVerifing(player)) {
					player.sendMessage(parent.main.msg("verifing"));
					return;
				}
				if (args.length == 2) {
					if (!args[1].contains("_BAY.")) {
						player.sendMessage(parent.main.msg("microsoft.invalid-link"));
						return;
					}
					parent.main.getVerifyManager().runMicrosoftLogin(player, args[1]);
					return;
				}
				for (String str : parent.main.msgs("microsoft.help")) {
					player.sendMessage(str.replace("<LINK>", Main.LOGIN_WEB_URL));
				}
				return;
			}

			if (args[0].equalsIgnoreCase("doomsdayverify") || args[0].equalsIgnoreCase("dv")) {
				event.setCancelled(true);
				if (args.length > 1) {
					List<String> a = Lists.newArrayList(args);
					a.remove(0);
					if (parent.sharedPart(player, args[0], a.parallelStream().toArray(String[]::new)))
						return;
					if (a.size() > 0 && a.get(0).equalsIgnoreCase("mojang")) {
						if (!parent.main.enableBugjump) {
							player.sendMessage(parent.main.msg("mojang.disabled"));
							return;
						}
						if (!player.hasPermission("doomsdayverify.mojang")) {
							player.sendMessage(parent.main.msg("no-permission"));
							return;
						}
						if (parent.main.getVerifyManager().isPlayerVerified(player)) {
							player.sendMessage(parent.main.msg("verified"));
							return;
						}
						if (parent.main.getVerifyManager().isPlayerVerifing(player)) {
							player.sendMessage(parent.main.msg("verifing"));
							return;
						}
						if (a.size() != 3) {
							player.sendMessage(parent.main.msgs("mojang.help"));
							return;
						}
						String email = a.get(1);
						String password = a.get(2);
						parent.main.getVerifyManager().runMojangLogin(player, email, password);
						return;
					}

				}

				player.sendMessage(parent.main.msgs("help"));
				if (player.isOp()) {
					player.sendMessage(parent.main.msgs("help-op"));
				}
			}
		}
	}

	Main main;
	boolean flag = false;

	public Commands(Main main) {
		super(main, ListenerPriority.NORMAL, PacketType.Play.Client.CHAT);
		this.main = main;
		this.main.getCommand("doomsdayverify").setExecutor(this);
		if (Bukkit.getPluginManager().getPlugin("ProtocolLib") != null) {
			ProtocolLibrary.getProtocolManager().addPacketListener(new ProtocolLibSupport(this));
			flag = true;
		} else {
			this.main.getLogger()
					.warning("You had not install ProtocolLib! We do not provide functions without ProtocolLib.");
		}
	}

	public void onDisable() {
		if (flag) {
			ProtocolLibrary.getProtocolManager().removePacketListeners(main);
		}
	}

	@Override
	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
		if (sender instanceof Player) {
			if (Bukkit.getPluginManager().getPlugin("ProtocolLib") == null)
				sender.sendMessage("Please contact administrator to install ProtocolLib to use this plugin.");
			return true;
		}
		sharedPart(sender, label, args);
		return true;
	}

	private boolean sharedPart(CommandSender sender, String label, String[] args) {
		if (!label.equalsIgnoreCase("doomsdayverify") && !label.equalsIgnoreCase("dv"))
			return false;
		if (args[0].equalsIgnoreCase("reload")) {
			if (!sender.hasPermission("doomsdayverify.reload")) {
				sender.sendMessage(main.msg("no-permission"));
				return true;
			}
			main.saveDefaultConfig();
			main.reloadConfig();
			sender.sendMessage(main.msg("reload"));
			return true;
		}
		if (args.length == 3 && args[0].equalsIgnoreCase("time")) {
			if (!sender.hasPermission("doomsdayverify.settime")) {
				sender.sendMessage(main.msg("no-permission"));
				return true;
			}
			String targetPlayer = args[1];
			int time = Util.strToInt(args[2], -1);
			if (Bukkit.getPlayer(targetPlayer) == null) {
				sender.sendMessage(main.msg("no-player"));
				return true;
			}
			if (time <= 0) {
				sender.sendMessage(main.msg("no-positive-integer"));
				return true;
			}
			main.getVerifyManager().setPlayerFailTime(targetPlayer, time);
			sender.sendMessage(
					main.msg("set-time").replace("%player%", targetPlayer).replace("%time%", String.valueOf(time)));
			return true;
		}
		/*
		 * DEBUG
		 * 
		 * if (args.length == 2 && args[0].equalsIgnoreCase("ms")) {
		 * Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
		 * fun.doomteam.verify.AuthUtil.LoginResult result =
		 * AuthUtil.microsoft(args[1]); sender.sendMessage(result.isSuccess() + ": " +
		 * result.getInfoString()); if(result.isSuccess()) {
		 * sender.sendMessage(result.getUuid() + ":"+result.getUsername()); } }); return
		 * true; }
		 * 
		 * /
		 **/
		if (!(sender instanceof Player)) {
			sender.sendMessage(main.msgs("help-op"));
			return true;
		}
		return false;
	}
}
