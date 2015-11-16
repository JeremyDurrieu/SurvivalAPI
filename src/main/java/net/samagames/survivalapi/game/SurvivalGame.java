package net.samagames.survivalapi.game;

import com.google.gson.JsonArray;
import com.google.gson.JsonPrimitive;
import net.minecraft.server.v1_8_R3.MinecraftServer;
import net.minecraft.server.v1_8_R3.SpawnerCreature;
import net.samagames.api.games.Game;
import net.samagames.api.games.Status;
import net.samagames.survivalapi.game.commands.CommandUHC;
import net.samagames.survivalapi.game.events.*;
import net.samagames.tools.ColorUtils;
import net.samagames.tools.Titles;
import net.samagames.tools.scoreboards.ObjectiveSign;
import org.bukkit.*;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Random;
import java.util.UUID;

public abstract class SurvivalGame<SURVIVALLOOP extends SurvivalGameLoop> extends Game<SurvivalPlayer>
{
    protected final JavaPlugin plugin;
    protected final Server server;
    protected final String magicSymbol;
    protected final Class<? extends SURVIVALLOOP> survivalGameLoopClass;
    protected final ArrayList<Location> spawns;
    protected final World world;

    protected LobbyPopulator lobbyPopulator;
    protected Location lobbySpawnLocation;
    protected SURVIVALLOOP gameLoop;
    protected Scoreboard scoreboard;
    protected BukkitTask mainTask;
    protected WorldBorder worldBorder;
    protected boolean damagesActivated;
    protected boolean pvpActivated;

    public SurvivalGame(JavaPlugin plugin, String gameCodeName, String gameName, String gameDescription, String magicSymbol, Class<? extends SURVIVALLOOP> survivalGameLoopClass)
    {
        super(gameCodeName, gameName, gameDescription, SurvivalPlayer.class);

        this.plugin = plugin;
        this.server = plugin.getServer();
        this.magicSymbol = magicSymbol;
        this.survivalGameLoopClass = survivalGameLoopClass;

        this.spawns = new ArrayList<>();
        this.world = this.server.getWorlds().get(0);

        this.gameLoop = null;
        this.scoreboard = null;
        this.mainTask = null;
        this.damagesActivated = false;
        this.pvpActivated = false;

        this.worldBorder = this.world.getWorldBorder();
        this.worldBorder.setCenter(0.0D, 0.0D);
        this.worldBorder.setWarningDistance(25);
        this.worldBorder.setDamageAmount(2.0D);
        this.worldBorder.setDamageBuffer(5.0D);

        plugin.getCommand("uhc").setExecutor(new CommandUHC(this));

        this.server.getPluginManager().registerEvents(new ChunkListener(plugin), plugin);
        this.server.getPluginManager().registerEvents(new GameListener(this), plugin);
        this.server.getPluginManager().registerEvents(new NaturalListener(), plugin);
        this.server.getPluginManager().registerEvents(new OptimizationListener(), plugin);
        this.server.getPluginManager().registerEvents(new SpectatorListener(this), plugin);

        SurvivalPlayer.setGame(this);
    }

    public abstract void teleport();
    public abstract void checkStump(Player player);

    @Override
    public void handlePostRegistration()
    {
        super.handlePostRegistration();

        this.setStatus(Status.STARTING);

        try
        {
            this.lobbyPopulator = new LobbyPopulator(this.plugin);
            this.lobbyPopulator.place();

            JsonArray defaults = new JsonArray();
            defaults.add(new JsonPrimitive(6D));
            defaults.add(new JsonPrimitive(199D));
            defaults.add(new JsonPrimitive(7));
            JsonArray spawnPos = this.gameManager.getGameProperties().getOption("spawnPos", defaults).getAsJsonArray();

            this.lobbySpawnLocation = new Location(this.world, spawnPos.get(0).getAsDouble(), spawnPos.get(1).getAsDouble(), spawnPos.get(2).getAsDouble());
            this.world.setSpawnLocation(this.lobbySpawnLocation.getBlockX(), this.lobbySpawnLocation.getBlockY(), this.lobbySpawnLocation.getBlockZ());

            this.gameLoop = this.survivalGameLoopClass.getConstructor(JavaPlugin.class, Server.class, SurvivalGame.class).newInstance(this.plugin, this.server, this);
            this.scoreboard = this.server.getScoreboardManager().getMainScoreboard();
        }
        catch (InstantiationException | IllegalAccessException | NoSuchMethodException | InvocationTargetException e)
        {
            e.printStackTrace();
        }

        this.setStatus(Status.WAITING_FOR_PLAYERS);
    }

    @Override
    public void handleGameEnd()
    {
        this.mainTask.cancel();
        super.handleGameEnd();
    }

    @Override
    public void handleReconnectTimeOut(Player player)
    {
        this.stumpPlayer(player, true);
    }

    @Override
    public void handleModeratorLogin(Player player)
    {
        super.handleModeratorLogin(player);
        this.rejoinPlayer(player);
    }

    @Override
    public void startGame()
    {
        super.startGame();

        this.lobbyPopulator.remove();

        Objective displayNameLife = this.scoreboard.registerNewObjective("vie", "health");
        Objective playerListLife = this.scoreboard.registerNewObjective("vieb", "health");

        displayNameLife.setDisplayName(ChatColor.RED + "♥");
        displayNameLife.setDisplaySlot(DisplaySlot.BELOW_NAME);
        playerListLife.setDisplayName(ChatColor.RED + "♥");
        playerListLife.setDisplaySlot(DisplaySlot.PLAYER_LIST);

        this.mainTask = this.server.getScheduler().runTaskTimer(this.plugin, this.gameLoop, 20, 20);
        this.teleport();

        for (UUID uuid : this.getInGamePlayers().keySet())
        {
            Player player = this.server.getPlayer(uuid);

            if (player == null)
            {
                this.gamePlayers.remove(uuid);
                continue;
            }

            player.setGameMode(GameMode.SURVIVAL);
            player.setHealth(player.getMaxHealth());
            player.setFoodLevel(20);
            player.setScoreboard(this.scoreboard);
            player.setLevel(0);
            player.getInventory().clear();

            displayNameLife.getScore(player.getName()).setScore((int) player.getHealth());
            playerListLife.getScore(player.getName()).setScore((int) player.getHealth());

            ObjectiveSign sign = new ObjectiveSign("sggameloop", ChatColor.GOLD + (this.magicSymbol != null ? this.magicSymbol + " " : "") + this.getGameName() + " " + (this.magicSymbol != null ? this.magicSymbol + " " : ""));
            sign.addReceiver(player);

            this.gameLoop.addPlayer(player.getUniqueId(), sign);
        }

        SpawnerCreature spawner = new SpawnerCreature();

        for (int i = 0; i < 3; i++)
            spawner.a(MinecraftServer.getServer().getWorldServer(0), false, true, true);
    }

    public void enableDamages()
    {
        this.damagesActivated = true;
    }

    public void enablePVP()
    {
        this.pvpActivated = true;
    }

    public void rejoinPlayer(Player player)
    {
        if (player != null)
        {
            this.server.getScheduler().runTaskLater(this.plugin, () ->
            {
                player.setScoreboard(this.scoreboard);

                ObjectiveSign sign = new ObjectiveSign("sggameloop", ChatColor.GOLD + (this.magicSymbol != null ? this.magicSymbol + " " : "") + this.getGameName() + " " + (this.magicSymbol != null ? this.magicSymbol + " " : ""));
                sign.addReceiver(player);

                this.gameLoop.addPlayer(player.getUniqueId(), sign);
            }, 10L);
        }
    }

    public void stumpPlayer(Player player, boolean logout)
    {
        if (this.status == Status.IN_GAME)
        {
            Object lastDamager = player.getMetadata("lastDamager").get(0);
            Player killer = null;

            if (lastDamager != null && lastDamager instanceof Player)
            {
                killer = (Player) lastDamager;

                if (killer.isOnline() && this.gamePlayers.containsKey(player.getUniqueId()) && !this.gamePlayers.get(player.getUniqueId()).isSpectator())
                {
                    final Player finalKiller = killer;

                    Bukkit.getScheduler().runTaskAsynchronously(plugin, () ->
                    {
                        SurvivalPlayer gamePlayer = this.getPlayer(finalKiller.getUniqueId());
                        gamePlayer.addKill(player.getUniqueId());
                        gamePlayer.addCoins(20, "Meurtre de " + player.getName());

                        this.increaseStat(finalKiller.getUniqueId(), "kills", 1);
                    });

                    killer.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 400, 1));
                }
                else
                {
                    killer = null;
                }
            }

            if (logout)
                this.coherenceMachine.getMessageManager().writePlayerReconnectTimeOut(player);
            else if (killer != null)
                this.server.broadcastMessage(this.coherenceMachine.getGameTag() + " " + player.getDisplayName() + ChatColor.YELLOW + " a été tué par " + killer.getDisplayName());
            else
                this.server.broadcastMessage(this.coherenceMachine.getGameTag() + " " + player.getDisplayName() + ChatColor.YELLOW + " est mort.");

            this.checkStump(player);
            this.removeFromGame(player.getUniqueId());

            if (!logout)
            {
                Bukkit.getScheduler().runTaskAsynchronously(this.plugin, () -> increaseStat(player.getUniqueId(), "deaths", 1));

                Titles.sendTitle(player, 0, 60, 5, ChatColor.DARK_RED + "✞", ChatColor.RED + "Vous êtes mort !");
                player.setGameMode(GameMode.SPECTATOR);
                player.setHealth(20.0D);
            }
        }
    }

    public void removeFromGame(UUID uuid)
    {
        SurvivalPlayer player = this.gamePlayers.get(uuid);

        if (player != null)
            player.setSpectator();
    }

    public void computeLocations()
    {
        this.spawns.add(new Location(this.world, 0, 150, 200));
        this.spawns.add(new Location(this.world, 0, 150, 400));
        this.spawns.add(new Location(this.world, 200, 150, 0));
        this.spawns.add(new Location(this.world, 400, 150, 0));
        this.spawns.add(new Location(this.world, 400, 150, 200));
        this.spawns.add(new Location(this.world, 200, 150, 400));
        this.spawns.add(new Location(this.world, 400, 150, 400));
        this.spawns.add(new Location(this.world, 200, 150, 200));
        this.spawns.add(new Location(this.world, 0, 150, -200));
        this.spawns.add(new Location(this.world, 0, 150, -400));
        this.spawns.add(new Location(this.world, -200, 150, 0));
        this.spawns.add(new Location(this.world, -400, 150, 0));
        this.spawns.add(new Location(this.world, -400, 150, -200));
        this.spawns.add(new Location(this.world, -200, 150, -400));
        this.spawns.add(new Location(this.world, -400, 150, -400));
        this.spawns.add(new Location(this.world, -200, 150, -200));
        this.spawns.add(new Location(this.world, 400, 150, -200));
        this.spawns.add(new Location(this.world, -400, 150, 200));
        this.spawns.add(new Location(this.world, 200, 150, -400));
        this.spawns.add(new Location(this.world, -200, 150, 400));
        this.spawns.add(new Location(this.world, 400, 150, -400));
        this.spawns.add(new Location(this.world, 200, 150, -200));

        Collections.shuffle(this.spawns);
    }

    public void effectsOnWinner(Player player)
    {
        this.server.getScheduler().scheduleSyncRepeatingTask(this.plugin, new Runnable()
        {
            int timer = 0;

            @Override
            public void run()
            {
                if (this.timer < 20)
                {
                    Firework fw = (Firework) player.getWorld().spawnEntity(player.getPlayer().getLocation(), EntityType.FIREWORK);
                    FireworkMeta fwm = fw.getFireworkMeta();
                    Random r = new Random();
                    int rt = r.nextInt(4) + 1;
                    FireworkEffect.Type type = FireworkEffect.Type.BALL;

                    if (rt == 1)
                        type = FireworkEffect.Type.BALL;
                    else if (rt == 2)
                        type = FireworkEffect.Type.BALL_LARGE;
                    else if (rt == 3)
                        type = FireworkEffect.Type.BURST;
                    else if (rt == 4)
                        type = FireworkEffect.Type.CREEPER;
                    else if (rt == 5)
                        type = FireworkEffect.Type.STAR;

                    int r1i = r.nextInt(15) + 1;
                    int r2i = r.nextInt(15) + 1;
                    Color c1 = ColorUtils.getColor(r1i);
                    Color c2 = ColorUtils.getColor(r2i);
                    FireworkEffect effect = FireworkEffect.builder().flicker(r.nextBoolean()).withColor(c1).withFade(c2).with(type).trail(r.nextBoolean()).build();
                    fwm.addEffect(effect);
                    int rp = r.nextInt(2) + 1;
                    fwm.setPower(rp);
                    fw.setFireworkMeta(fwm);
                    this.timer++;
                }
            }
        }, 5L, 5L);
    }

    private String getDamageCause(EntityDamageEvent.DamageCause cause)
    {
        switch (cause)
        {
            case SUFFOCATION:
                return "Suffocation";
            case FALL:
                return "Chute";
            case FIRE:
            case FIRE_TICK:
                return "Feu";
            case LAVA:
                return "Lave";
            case DROWNING:
                return "Noyade";
            case BLOCK_EXPLOSION:
            case ENTITY_EXPLOSION:
                return "Explosion";
            case LIGHTNING:
                return "Foudre";
            case POISON:
                return "Poison";
            case MAGIC:
                return "Potion";
            case FALLING_BLOCK:
                return "Chute de blocs";
            default:
                return "Autre";
        }
    }

    public JavaPlugin getPlugin()
    {
        return this.plugin;
    }

    public Location getLobbySpawn()
    {
        return this.lobbySpawnLocation;
    }

    public Scoreboard getScoreboard()
    {
        return this.scoreboard;
    }

    public SURVIVALLOOP getSurvivalGameLoop()
    {
        return this.gameLoop;
    }

    public WorldBorder getWorldBorder()
    {
        return this.worldBorder;
    }

    public boolean isDamagesActivated()
    {
        return this.damagesActivated;
    }

    public boolean isPvPActivated()
    {
        return this.pvpActivated;
    }
}