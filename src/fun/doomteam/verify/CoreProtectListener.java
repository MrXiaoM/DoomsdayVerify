package fun.doomteam.verify;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.plugin.RegisteredListener;

public class CoreProtectListener implements Listener{
	Main main;
	public CoreProtectListener(Main main) {
		this.main = main;
		
	}
    @EventHandler(priority=EventPriority.HIGHEST)
    public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent event) {
    	// System.out.println(event.getMessage());
    	if(event.getMessage().startsWith("/doomsdayverify") || event.getMessage().startsWith("/dv")) {
    		for(RegisteredListener rl : event.getHandlers().getRegisteredListeners()) {
        		if(rl.getPlugin().getName().equalsIgnoreCase("coreprotect")) {
        			// TODO hook CoreProtect
        			// System.out.println("ÒÑĞ¶ÔØ CoreProtect µÄÃüÁî¼àÌıÆ÷");
        			event.getHandlers().unregister(rl);
        		}
        	}
        }
    }
}
