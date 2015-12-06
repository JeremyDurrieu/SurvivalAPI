package net.samagames.survivalapi.game;

import net.samagames.survivalapi.game.types.SurvivalTeamGame;
import net.samagames.survivalapi.utils.TimedEvent;
import net.samagames.tools.Titles;
import net.samagames.tools.chat.ActionBarAPI;
import net.samagames.tools.scoreboards.ObjectiveSign;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SurvivalGameLoop implements Runnable
{
    protected final JavaPlugin plugin;
    protected final Server server;
    protected final SurvivalGame game;
    protected final World world;
    protected final ConcurrentHashMap<UUID, ObjectiveSign> objectives;

    protected TimedEvent nextEvent;
    protected int minutes;
    protected int seconds;
    protected int episode;
    protected boolean episodeEnabled;

    public SurvivalGameLoop(JavaPlugin plugin, Server server, SurvivalGame game)
    {
        this.game = game;
        this.plugin = plugin;
        this.server = server;
        this.world = server.getWorlds().get(0);
        this.objectives = new ConcurrentHashMap<>();

        this.seconds = 0;
        this.minutes = 0;
        this.episode = 1;

        this.episodeEnabled = false;

        this.createDamageEvent();
    }

    public void createDamageEvent()
    {
        this.nextEvent = new TimedEvent(1, 0, "Dégats actifs", ChatColor.GREEN, () ->
        {
            this.game.getCoherenceMachine().getMessageManager().writeCustomMessage("Les dégats sont désormais actifs.", true);
            this.game.enableDamages();

            this.createPvPEvent();
        });
    }

    public void createPvPEvent()
    {
        this.nextEvent = new TimedEvent(19, 0, "Combats actifs", ChatColor.YELLOW, () ->
        {
            this.game.getCoherenceMachine().getMessageManager().writeCustomMessage("Les combats sont désormais actifs.", true);
            this.game.enablePVP();

            this.createReducingEvent();
        });
    }

    public void createReducingEvent()
    {
        this.nextEvent = new TimedEvent(40, 0, "Réduction des bordures", ChatColor.RED, () ->
        {
            this.game.getWorldBorder().setSize(100, 60L * 40L);
            this.displayReducingMessage();
        });
    }

    public void displayReducingMessage()
    {
        for (Player player : Bukkit.getOnlinePlayers())
        {
            Titles.sendTitle(player, 0, 100, 5, ChatColor.RED + "Attention !", ChatColor.YELLOW + "Les bordures se réduisent !");
            player.playSound(player.getLocation(), Sound.BLAZE_DEATH, 1.0F, 1.0F);
        }

        this.game.getCoherenceMachine().getMessageManager().writeCustomMessage(ChatColor.RED + "Les bordures se réduisent !", true);
    }

    public void forceNextEvent()
    {
        if (this.nextEvent != null)
            this.nextEvent.run();
    }

    public void addPlayer(UUID uuid, ObjectiveSign sign)
    {
        this.objectives.put(uuid, sign);
    }

    public void removePlayer(UUID uuid)
    {
        this.objectives.remove(uuid);
    }

    @Override
    public void run()
    {
        this.seconds++;

        if (this.seconds >= 60)
        {
            this.minutes++;
            this.seconds = 0;

            if (this.episodeEnabled)
            {
                if (this.minutes >= 20)
                {
                    this.game.getCoherenceMachine().getMessageManager().writeCustomMessage("Fin de l'épisode " + this.episode, true);
                    this.episode++;

                    this.minutes = 0;
                }
            }
        }

        for (UUID playerUUID : this.objectives.keySet())
        {
            ObjectiveSign objective = this.objectives.get(playerUUID);
            Player player = this.server.getPlayer(playerUUID);

            objective.clearScores();

            if (player == null)
            {
                this.server.getLogger().info("Player null : " + playerUUID);
                this.objectives.remove(playerUUID);
            }
            else
            {
                objective.setLine(0, ChatColor.DARK_RED + "");
                objective.setLine(1, ChatColor.GRAY + "Joueurs : " + ChatColor.WHITE + this.game.getInGamePlayers().size());

                int lastLine = 1;

                if (this.game instanceof SurvivalTeamGame)
                {
                    objective.setLine(lastLine + 1, ChatColor.GRAY + "Équipes : " + ChatColor.WHITE + ((SurvivalTeamGame) this.game).getTeams().size());
                    lastLine++;
                }

                objective.setLine(lastLine + 1, ChatColor.RED + "");
                lastLine++;

                if (this.nextEvent != null)
                    ActionBarAPI.sendMessage(player, this.nextEvent.getColor().toString() + this.nextEvent.getName() + " dans " + this.toString(this.nextEvent.getSeconds() == 0 ? this.nextEvent.getMinutes() - 1 : this.nextEvent.getMinutes(), this.nextEvent.getSeconds() == 0 ? 59 : this.nextEvent.getSeconds() - 1));

                SurvivalPlayer gamePlayer = (SurvivalPlayer) this.game.getPlayer(playerUUID);
                int kills = gamePlayer == null ? 0 : gamePlayer.getKills().size();

                objective.setLine((lastLine + 1), ChatColor.GRAY + "Joueurs tués : " + ChatColor.WHITE + kills);
                objective.setLine((lastLine + 2), ChatColor.AQUA + "");

                lastLine += 2;

                if (this.game instanceof SurvivalTeamGame)
                {
                    if (gamePlayer != null && gamePlayer.getTeam() != null)
                    {
                        int teammates = 0;

                        for (UUID teammateUUID : gamePlayer.getTeam().getPlayersUUID().keySet())
                        {
                            if (playerUUID.equals(teammateUUID))
                                continue;

                            teammates++;

                            Player teammate = Bukkit.getPlayer(teammateUUID);

                            if (teammate == null)
                                objective.setLine((lastLine + teammates), ChatColor.RED + "× " + Bukkit.getOfflinePlayer(teammateUUID).getName() + " : Déconnecté");
                            else if (this.game.getPlayer(teammateUUID).isSpectator())
                                objective.setLine((lastLine + teammates), ChatColor.RED + "× " + teammate.getName() + " : ✞");
                            else
                                objective.setLine((lastLine + teammates), this.getPrefixColorByHealth(teammate.getHealth(), teammate.getMaxHealth()) + this.getDirection(player, teammate) + " " + teammate.getName() + ChatColor.WHITE + " : " + (int) teammate.getHealth() + ChatColor.RED + " ❤");
                        }

                        objective.setLine((lastLine + (teammates + 1)), ChatColor.DARK_PURPLE + "");

                        lastLine += (teammates + 1);
                    }
                }

                objective.setLine((lastLine + 1), ChatColor.GRAY + "Bordure :");
                objective.setLine((lastLine + 2), ChatColor.WHITE + "-" + (int) this.world.getWorldBorder().getSize() / 2 + " +" + (int) this.world.getWorldBorder().getSize() / 2);
                objective.setLine((lastLine + 3), ChatColor.LIGHT_PURPLE + "");
                objective.setLine((lastLine + 4), ChatColor.WHITE + this.toString(this.minutes, this.seconds));

                objective.updateLines();

                this.server.getScheduler().runTaskAsynchronously(this.plugin, objective::updateLines);
            }
        }

        if (this.nextEvent.getSeconds() == 0 && this.nextEvent.getMinutes() <= 3 && this.nextEvent.getMinutes() > 0 || this.nextEvent.getMinutes() == 0 && (this.nextEvent.getSeconds() <= 5 || this.nextEvent.getSeconds() == 10 || this.nextEvent.getSeconds() == 30))
            this.game.getCoherenceMachine().getMessageManager().writeCustomMessage(ChatColor.YELLOW + this.nextEvent.getName() + ChatColor.YELLOW + " dans " + (this.nextEvent.getMinutes() != 0 ? this.nextEvent.getMinutes() + " minutes" : this.nextEvent.getSeconds() + " secondes") + ".", true);

        if (this.nextEvent.getSeconds() == 0 && this.nextEvent.getMinutes() == 0)
            this.game.getCoherenceMachine().getMessageManager().writeCustomMessage(ChatColor.YELLOW + this.nextEvent.getName() + ChatColor.YELLOW + " maintenant !", true);

        this.nextEvent.decrement();
    }

    private ChatColor getPrefixColorByHealth(double health, double max)
    {
        double q = max / 4;

        if (health < q)
            return ChatColor.RED;
        else if (health < (q * 2))
            return ChatColor.YELLOW;
        else if (health < (q * 3))
            return ChatColor.GREEN;
        else
            return ChatColor.DARK_GREEN;
    }

    private String getDirection(Player p, Player mate)
    {
        Location ploc = p.getLocation().clone();
        Location point = mate.getLocation().clone();

        // on ignore l'axe y
        ploc.setY(0);
        point.setY(0);

        // on récupère la  direction de la tête du player
        Vector d = ploc.getDirection();

        // on récupère la direction du point par rapport au player
        Vector v = point.subtract(ploc).toVector().normalize();

        // on convertit le tout en un angle en degrés
        double a = Math.toDegrees(Math.atan2(d.getX(), d.getZ()));
        a -= Math.toDegrees(Math.atan2(v.getX(), v.getZ()));

        // on se décale de 22.5 degrés pour se caler sur les demi points cardinaux
        a = (int)(a + 22.5) % 360;

        // on  s'assure d'avoir un angle strictement positif
        if (a < 0)
            a += 360;

        return "" + "⬆⬈➡⬊⬇⬋⬅⬉".charAt((int)a / 45);
    }

    //OLD
    private String getDirectionalArrow(Player base, Player teammate)
    {
        double deltaX = teammate.getLocation().getX() - base.getLocation().getX();
        double deltaZ = teammate.getLocation().getZ() - base.getLocation().getZ();

        double temp = Math.atan2(deltaZ, deltaX) * 180 / Math.PI + 180;
        double angle = Math.abs((base.getEyeLocation().getYaw() - 90 - temp) % 360);

        if (angle > 337.5 || angle < 22.5)
            return "⬆";
        else if (angle > 22.5 && angle < 67.5)
            return "⬈";
        else if (angle > 67.5 && angle < 112.5)
            return "➡";
        else if (angle > 112.5 && angle < 157.5)
            return "⬊";
        else if (angle > 157.5 && angle < 202.5)
            return "⬇";
        else if (angle > 202.5 && angle < 247.5)
            return "⬋";
        else if (angle > 247.5 && angle < 292.5)
            return "⬅";
        else
            return "⬉";
    }

    private String toString(int minutes, int seconds)
    {
        return (minutes < 10 ? "0" : "") + minutes + ":" + (seconds < 10 ? "0" : "") + seconds;
    }
}
