package net.samagames.survivalapi.modules.gameplay;

import net.samagames.api.games.Game;
import net.samagames.api.games.GamePlayer;
import net.samagames.survivalapi.SurvivalAPI;
import net.samagames.survivalapi.SurvivalPlugin;
import net.samagames.survivalapi.modules.AbstractSurvivalModule;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Collection;
import java.util.HashMap;

public class CatsEyesModule extends AbstractSurvivalModule
{
    public CatsEyesModule(SurvivalPlugin plugin, SurvivalAPI api, HashMap<String, Object> moduleConfiguration)
    {
        super(plugin, api, moduleConfiguration);
    }

    /**
     * Give a night vision effect to every players
     *
     * @param game Game
     */
    @Override
    public void onGameStart(Game game)
    {
        for (GamePlayer player : (Collection<GamePlayer>) game.getInGamePlayers())
            player.getPlayerIfOnline().addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 255, 255));
    }

    /**
     * Re-give the night vision effect if the player drinks milk
     *
     * @param event Event
     */
    @EventHandler
    public void onPlayerItemConsume(PlayerItemConsumeEvent event)
    {
        if (event.getItem().getType() == Material.MILK_BUCKET)
            event.getPlayer().addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 255, 255));
    }
}