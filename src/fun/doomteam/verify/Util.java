package fun.doomteam.verify;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

import me.clip.placeholderapi.PlaceholderAPI;

public class Util {
	public static final String nms = Bukkit.getServer().getClass().getPackage().getName().substring(23);
	private static boolean isUsePlaceholderAPI = false;
	
	public static boolean init() {
		try {
			if(Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
				Class.forName("me.clip.placeholderapi.PlaceholderAPI");
				isUsePlaceholderAPI = true;
			}
		}catch(Throwable t) {
			isUsePlaceholderAPI = false;
		}
		return isUsePlaceholderAPI;
	}
	
	public static void sendTellraw(Player player, String msg) {
		try {
			Class<?> classPacketChat = Class.forName("net.minecraft.server." + nms + ".PacketPlayOutChat");
			Class<?> classIChatBase = Class.forName("net.minecraft.server." + nms + ".IChatBaseComponent");
			Class<?> classChatSeri = classIChatBase.getDeclaredClasses()[0];
			Method a = classChatSeri.getDeclaredMethod("a", String.class);
			Constructor<?> constPacketChat = classPacketChat.getDeclaredConstructor(classIChatBase);
			Object text = a.invoke(null, msg);
			Object packet = constPacketChat.newInstance(text);
			sendPacket(player, packet);
		}catch(Throwable t) {
			t.printStackTrace();
		}
	}
	
	public static void sendActionMsg(Player player, String msg) {
		try {
			Class<?> classPacket = Class.forName("net.minecraft.server." + nms + ".PacketPlayOutChat");
			Class<?> classChatText = Class.forName("net.minecraft.server." + nms + ".ChatComponentText");
			Class<?> classIChatBase = Class.forName("net.minecraft.server." + nms + ".IChatBaseComponent");
			Constructor<?> constChatText = classChatText.getDeclaredConstructor(String.class);
			Constructor<?> constPacket = classPacket.getDeclaredConstructor(classIChatBase, byte.class);
			Object text = constChatText.newInstance(ChatColor.translateAlternateColorCodes('&', msg));
			Object packet = constPacket.newInstance(text, (byte) 2);
			sendPacket(player, packet);
		} catch (Throwable t) {
			t.printStackTrace();
		}
	}

	public static void sendPacket(Player player, Object packet) {
		try {
			Class<?> classCraftPlayer = Class.forName("org.bukkit.craftbukkit." + nms + ".entity.CraftPlayer");
			Class<?> classPlayer = Class.forName("net.minecraft.server." + nms + ".EntityPlayer");
			Class<?> classConnection = Class.forName("net.minecraft.server." + nms + ".PlayerConnection");
			Class<?> classPacket = Class.forName("net.minecraft.server." + nms + ".Packet");
			Method getNMSPlayer = classCraftPlayer.getDeclaredMethod("getHandle");
			Object nmsPlayer = getNMSPlayer.invoke(player);
			Field fieldConnection = classPlayer.getDeclaredField("playerConnection");
			Object conn = fieldConnection.get(nmsPlayer);
			Method sendPacket = classConnection.getDeclaredMethod("sendPacket", classPacket);
			sendPacket.invoke(conn, packet);
		} catch (Throwable t) {
			t.printStackTrace();
		}
	}

	public static void sendTitle(Player player, int in, int time, int out, String msg) {
		sendTitle(player, "TITLE", in, time, out, msg);
	}

	public static void sendTitle(Player player, String type, int in, int time, int out, String msg) {
		try {
			Class<?> classPacket = Class.forName("net.minecraft.server." + nms + ".PacketPlayOutTitle");
			Class<?> classPacketAction = classPacket.getDeclaredClasses()[0];
			Class<?> classIChatBase = Class.forName("net.minecraft.server." + nms + ".IChatBaseComponent");
			Class<?> classChatText = Class.forName("net.minecraft.server." + nms + ".ChatComponentText");
			Constructor<?> constChatText = classChatText.getDeclaredConstructor(String.class);
			Constructor<?> constPacket = classPacket.getDeclaredConstructor(classPacketAction, classIChatBase,
					int.class, int.class, int.class);
			Object text = constChatText.newInstance(ChatColor.translateAlternateColorCodes('&', msg));
			Method methodValues = classPacketAction.getDeclaredMethod("valueOf", String.class);
			Object value = methodValues.invoke(null, type.toUpperCase());
			Object packet = constPacket.newInstance(value, text, in, time, out);
			sendPacket(player, packet);
		} catch (Throwable t) {
			t.printStackTrace();
		}
	}
	
	private static List<String> handlePlaceholder(Player player, List<String> str) {
		List<String> list = new ArrayList<>();
		for(String s : str) {
			list.add(handlePlaceholder(player, s));
		}
		return list;
	}
	
	private static String handlePlaceholder(Player player, String str) {
		if(!isUsePlaceholderAPI) return ChatColor.translateAlternateColorCodes('&', str);
		return PlaceholderAPI.setPlaceholders(player, str);
	}
	
	public static void runCommands(List<String> commands, Player player) {
		if (player == null || commands == null || commands.isEmpty())
			return;
		for (String cmd : handlePlaceholder(player, commands)) {
			if (cmd.startsWith("console:")) {
				Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.substring(8));
				continue;
			}
			if (cmd.startsWith("message-all:")) {
				Bukkit.broadcastMessage(cmd.substring(12));
				continue;
			}
			if (player.isOnline()) {
				if (cmd.startsWith("player:")) {
					Bukkit.dispatchCommand(player, cmd.substring(7));
					continue;
				}
				if (cmd.startsWith("sound:")) {
					String s = cmd.substring(6).toUpperCase();
					String[] a = s.contains(",") ? s.split(",") : new String[] { s };
					float volume = strToFloat(a.length > 1 ? a[1] : "1.0", 1.0F);
					float pitch = strToFloat(a.length > 2 ? a[2] : "1.0", 1.0F);
					player.playSound(player.getLocation(), Sound.valueOf(a[0]), volume, pitch);
					continue;
				}
				if (cmd.startsWith("message:")) {
					player.sendMessage(cmd.substring(8));
					continue;
				}
				if (cmd.startsWith("action:")) {
					sendActionMsg(player, cmd.substring(7));
					continue;
				}
				if (cmd.startsWith("title:")) {
					sendTitle(player, "TITLE", 10, 40, 10, cmd.substring(6));
					continue;
				}
				if (cmd.startsWith("subtitle:")) {
					sendTitle(player, "SUBTITLE", 10, 40, 10, cmd.substring(9));
					continue;
				}
			}
		}
	}

	public static void clearPlayerEffects(Player player) {
		if (player == null)
			return;
		for (PotionEffectType type : PotionEffectType.values()) {
			if (type == null)
				continue;
			if (player.hasPotionEffect(type)) {
				player.removePotionEffect(type);
			}
		}
	}

	public static Integer strToInt(String str, Integer nullValue) {
		try {
			return Integer.valueOf(str);
		} catch (Throwable t) {
			return nullValue;
		}
	}

	public static Float strToFloat(String str, Float nullValue) {
		try {
			return Float.valueOf(str);
		} catch (Throwable t) {
			return nullValue;
		}
	}
}
