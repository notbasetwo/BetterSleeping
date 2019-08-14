package be.dezijwegel.events;

import be.dezijwegel.management.Management;
import com.earth2me.essentials.Essentials;
import com.earth2me.essentials.User;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.*;

public class SleepTracker {

    private Map<UUID, Long> sleepList;
    private Map<World, Integer> numSleeping;
    private Map<World, Long> lastSetToDay;

    private Management management;
    private Essentials essentials = null;

    private boolean isEssentialsHooked;
    private boolean multiworld;
    private int bedEnterDelay;
    private int percentageNeeded;

    public SleepTracker(Management management)
    {
        isEssentialsHooked = Bukkit.getPluginManager().isPluginEnabled("Essentials");
        if (isEssentialsHooked) {
            essentials = (Essentials) Bukkit.getServer().getPluginManager().getPlugin("Essentials");
            Bukkit.getConsoleSender().sendMessage("[BetterSleeping] " + ChatColor.GREEN + "Succesfully hooked into Essentials!");
        } else {
            Bukkit.getConsoleSender().sendMessage("[BetterSleeping] Essentials was not found on this server!");
        }

        sleepList = new HashMap<UUID, Long>();
        numSleeping = new HashMap<World, Integer>();
        lastSetToDay = new HashMap<World, Long>();

        this.management = management;

        this.multiworld = management.getBooleanSetting("multiworld_support");
        this.bedEnterDelay = management.getIntegerSetting("bed_enter_delay");
        this.percentageNeeded = management.getIntegerSetting("percentage_needed");
        if (percentageNeeded > 100) percentageNeeded = 100;
        else if (percentageNeeded < 0) percentageNeeded = 0;
    }


    /**
     * Get how many seconds ago the time was set to day
     * @param world
     * @return
     */
    public int getTimeSinceLastSetToDay(World world)
    {
        if (lastSetToDay.containsKey(world)) {
            return (int) ( System.currentTimeMillis() / 1000 - lastSetToDay.get(world) );
        } else return 3600;
    }


    /**
     * Add the current time to the list of lastSetToDay
     * @param world
     */
    public void worldWasSetToDay(World world)
    {
        lastSetToDay.put(world, System.currentTimeMillis()/1000);
    }


    /**
     * Check whether or not a Player (by uuid) must wait a while before they can sleep (again)
     * @param uuid
     * @return
     */
    public boolean checkPlayerSleepDelay (UUID uuid)
    {
        long currentTime = System.currentTimeMillis() / 1000L;
        if (sleepList.containsKey(uuid))
        {
            if (whenCanPlayerSleep(uuid) == 0)
            {
                sleepList.put(uuid, currentTime);
                return true;
            } else {
                return false;
            }
        } else {
            sleepList.put(uuid, currentTime);
            return true;
        }
    }


    /**
     * Gets the time (seconds) a Player must wait before they can sleep again
     * @param uuid
     * @return
     */
    public long whenCanPlayerSleep(UUID uuid)
    {
        if (sleepList.containsKey(uuid))
        {
            long currentTime = System.currentTimeMillis() / 1000L;
            long deltaTime = currentTime - sleepList.get(uuid);
            if(deltaTime < bedEnterDelay) {
                long temp = bedEnterDelay - deltaTime;
                return temp;
            }else return 0L;

        } else {
            return 0L;
        }
    }


    /**
     * Get the number of players that should be sleeping for the night to be skipped
     * This method also considers the 'multiworld_support' setting in config.yml
     * @param world
     * @return
     */
    public int getTotalSleepersNeeded(World world)
    {

        int numPlayers = 0;
        if (multiworld)
        {
            for (Player player : Bukkit.getOnlinePlayers())
            {
                if (player.getLocation().getWorld().equals(world)) {
                    if (! isAfk( player ) && ! isPlayerBypassed( player ))
                        numPlayers++;
                }
            }
        }
        else
        {
            numPlayers = Bukkit.getOnlinePlayers().size();
            for (Player p : Bukkit.getOnlinePlayers())
            {
                if ( isPlayerBypassed( p ) || isAfk( p ) )
                    numPlayers--;
            }
        }

        int numNeeded = (int) Math.ceil((double)(percentageNeeded * numPlayers) / 100);
        if (numNeeded > 1) return numNeeded;
        else return 1;
    }


    /**
     * Check whether or not the given player should be counted towards needed sleeping players
     * @param player
     * @return
     */
    private boolean isAfk( Player player )
    {
        boolean isAfk = false;

        if (isEssentialsHooked && management.getBooleanSetting("essentials_afk_support"))
        {
            User user = essentials.getUser( player );
            if (user.isAfk()) isAfk = true;
        }

        return isAfk;
    }


    /**
     * Get the number of relevant players that are currently sleeping
     * The 'multiworld_support' option will be considered
     * @param world
     * @return
     */
    public int getNumSleepingPlayers(World world)
    {
        int numSleeping;
        if (multiworld)
        {
            if (this.numSleeping.get(world) == null)
                numSleeping = 0;
            else
                numSleeping = this.numSleeping.get(world);
        }
        else
        {
            numSleeping = 0;
            for (Map.Entry<World, Integer> entry : this.numSleeping.entrySet())
            {
                if (entry.getValue() == null)
                    numSleeping = 0;
                else
                    numSleeping += entry.getValue();
            }
        }

        return numSleeping;
    }


    /**
     * Add a player to the sleeping list
     * @param world
     */
    public void addSleepingPlayer(World world)
    {
        if (numSleeping.containsKey(world))
        {
            int num = numSleeping.get(world) + 1;
            numSleeping.put(world, num);
        } else
        {
            numSleeping.put(world, 1);
        }
    }


    /**
     * Remove a player from the sleeping list
     * @param world
     */
    public void removeSleepingPlayer(World world)
    {
        if (numSleeping.containsKey(world))
        {
            int num = numSleeping.get(world) -1;

            if (num < 0) num = 0;

            numSleeping.put(world, num);
        } else
        {
            numSleeping.put(world, 0);
        }
    }


    /**
     * Get a list of players that are relevant to the given world
     * Does take 'multiworld_support' into account
     * @param world
     * @return
     */
    public List<Player> getRelevantPlayers(World world)
    {
        List<Player> list = new LinkedList<Player>();
        if (multiworld)
        {
            for (Player player : Bukkit.getOnlinePlayers())
                if (player.getLocation().getWorld().equals(world)) list.add(player);
        }
        else
        {
            for (Player player : Bukkit.getOnlinePlayers())
                list.add(player);
        }

        return list;
    }


    /**
     * Check if a Player meets the requirements to sleep
     * Also checks if the World meets the requirements
     * Sends messages to players if needed
     * @return
     */
    public boolean playerMaySleep(Player player)
    {
        World worldObj = player.getWorld();
        if (worldObj.getTime() > 12500 || worldObj.hasStorm() || worldObj.isThundering()) {

            UUID uuid = player.getUniqueId();

            if ( isPlayerBypassed(player) )
            {
                management.sendMessage("bypass_message", player);
                return false;
            }
            if (checkPlayerSleepDelay(uuid))
                return true;
            else {
                Map<String, String> replace = new LinkedHashMap<String, String>();
                long time = whenCanPlayerSleep(player.getUniqueId());
                replace.put("<time>", Long.toString(time));
                boolean isSingular;
                if (time == 1) isSingular = true; else isSingular = false;
                management.sendMessage("sleep_spam", player, replace, isSingular);
                return false;
            }
        }
        return false;
    }


    /**
     * Check if the player has any sleeping bypass permissions
     * @param player
     * @return
     */
    public boolean isPlayerBypassed(Player player)
    {
        // Permission based
        if (player.hasPermission("essentials.sleepingignored"))     return true;
        if (player.hasPermission("bettersleeping.bypass"))          return true;

        // Gamemode based bypassing
        boolean ignoreCreative = management.getBooleanSetting("ignore_creative");
        if (ignoreCreative && player.getGameMode() == GameMode.CREATIVE) return true;

        boolean ignoreSpectator = management.getBooleanSetting("ignore_spectator");
        if (ignoreSpectator && player.getGameMode() == GameMode.SPECTATOR) return true;

        boolean ignoreAdventure = management.getBooleanSetting("ignore_adventure");
        if (ignoreAdventure && player.getGameMode() == GameMode.ADVENTURE) return true;

        boolean ignoreSurvival = management.getBooleanSetting("ignore_survival");
        if (ignoreSurvival && player.getGameMode() == GameMode.SURVIVAL) return true;

        // Otherwise it is not a bypassing player
        return false;
    }


    /**
     * Stop keeping track of when the given Player last slept
     * @param player
     */
    public void playerLogout(Player player)
    {
        if (sleepList.containsKey(player.getUniqueId()))
            sleepList.remove(player.getUniqueId());
    }
}
