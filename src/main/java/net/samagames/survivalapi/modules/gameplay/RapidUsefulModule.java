package net.samagames.survivalapi.modules.gameplay;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.samagames.survivalapi.SurvivalAPI;
import net.samagames.survivalapi.SurvivalPlugin;
import net.samagames.survivalapi.modules.AbstractSurvivalModule;
import net.samagames.survivalapi.modules.IConfigurationBuilder;
import net.samagames.survivalapi.modules.utility.DropTaggingModule;
import net.samagames.survivalapi.utils.Meta;
import net.samagames.tools.ItemUtils;
import org.apache.commons.lang.Validate;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/*
 * This file is part of SurvivalAPI.
 *
 * SurvivalAPI is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SurvivalAPI is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SurvivalAPI.  If not, see <http://www.gnu.org/licenses/>.
 */
public class RapidUsefulModule extends AbstractSurvivalModule
{
    private final Map<ItemStack, ConfigurationBuilder.IRapidUsefulHook> drops;
    private final Random random;

    /**
     * Constructor
     *
     * @param plugin Parent plugin
     * @param api API instance
     * @param moduleConfiguration Module configuration
     */
    public RapidUsefulModule(SurvivalPlugin plugin, SurvivalAPI api, Map<String, Object> moduleConfiguration)
    {
        super(plugin, api, moduleConfiguration);
        Validate.notNull(moduleConfiguration, "Configuration cannot be null!");

        this.drops = (Map<ItemStack, ConfigurationBuilder.IRapidUsefulHook>) moduleConfiguration.get("drops");
        this.random = new Random();
    }

    /**
     * Drop some utilities
     *
     * @param event Event
     */
    @EventHandler
    public void onItemSpawn(ItemSpawnEvent event)
    {
        if (event.getEntityType() != EntityType.DROPPED_ITEM)
            return;

        if (Meta.hasMeta(event.getEntity().getItemStack()))
            return;

        ItemStack stack = event.getEntity().getItemStack();

        for (ItemStack drop : this.drops.keySet())
        {
            if (drop.getType() == stack.getType())
            {
                if (drop.getDurability() == -1 || (drop.getDurability() == stack.getDurability()))
                {
                    ItemStack finalDrop = this.drops.get(drop).getDrop(stack, this.random);

                    if (finalDrop == null)
                        event.setCancelled(true);
                    else
                        event.getEntity().setItemStack(finalDrop);

                    break;
                }
            }
        }
    }

    /**
     * Increase the xp dropped
     *
     * @param event Event
     */
    @EventHandler
    public void onEntityDeath(EntityDeathEvent event)
    {
        event.setDroppedExp(event.getDroppedExp() * 2);
    }

    @Override
    public List<Class<? extends AbstractSurvivalModule>> getRequiredModules()
    {
        List<Class<? extends AbstractSurvivalModule>> requiredModules = new ArrayList<>();

        requiredModules.add(DropTaggingModule.class);

        return requiredModules;
    }

    public static class ConfigurationBuilder implements IConfigurationBuilder
    {
        private final Map<ItemStack, IRapidUsefulHook> drops;

        public ConfigurationBuilder()
        {
            this.drops = new HashMap<>();
        }

        @Override
        public Map<String, Object> build()
        {
            Map<String, Object> moduleConfiguration = new HashMap<>();

            moduleConfiguration.put("drops", this.drops);

            return moduleConfiguration;
        }

        @Override
        public Map<String, Object> buildFromJson(Map<String, JsonElement> configuration) throws Exception
        {
            if (configuration.containsKey("drops"))
            {
                JsonArray dropsJson = configuration.get("drops").getAsJsonArray();

                for (int i = 0; i < dropsJson.size(); i++)
                {
                    JsonObject dropJson = dropsJson.get(i).getAsJsonObject();

                    ItemStack match = ItemUtils.strToStack(dropJson.get("match").getAsString());
                    ItemStack stack = ItemUtils.strToStack(dropJson.get("stack").getAsString());
                    double chance = dropJson.get("chance").getAsDouble();

                    this.addDrop(match, (base, random) ->
                    {
                        if (random.nextDouble() <= chance)
                            return stack;
                        else
                            return base;
                    }, true);
                }
            }

            return this.build();
        }

        public ConfigurationBuilder addDefaults()
        {
            this.addDrop(new ItemStack(Material.GRAVEL, 1), (base, random) ->
            {
                if (random.nextDouble() <= 0.75D)
                    return new ItemStack(Material.ARROW, 3);
                else
                    return base;
            }, false);

            this.addDrop(new ItemStack(Material.FLINT, 1), (base, random) ->
            {
                if (random.nextDouble() <= 0.75D)
                    return new ItemStack(Material.ARROW, 3);
                else
                    return base;
            }, false);

            this.addDrop(new ItemStack(Material.SAPLING, 1, (short) -1), (base, random) ->
            {
                if (random.nextDouble() <= 0.3D)
                    return new ItemStack(Material.APPLE, 1);
                else
                    return null;
            }, false);

            this.addDrop(new ItemStack(Material.SAND, 1), (base, random) -> new ItemStack(Material.GLASS_BOTTLE, 1), false);
            this.addDrop(new ItemStack(Material.CACTUS, 1), (base, random) -> new ItemStack(Material.LOG, 2), false);
            this.addDrop(new ItemStack(Material.SULPHUR, 1), (base, random) -> Meta.addMeta(new ItemStack(Material.TNT, 1)), false);

            return this;
        }

        public ConfigurationBuilder addDrop(ItemStack base, IRapidUsefulHook rapidFoodHook, boolean override)
        {
            if (!this.drops.containsKey(base))
            {
                this.drops.put(base, rapidFoodHook);
            }
            else if (override)
            {
                this.drops.remove(base);
                this.drops.put(base, rapidFoodHook);
            }

            return this;
        }

        public interface IRapidUsefulHook
        {
            ItemStack getDrop(ItemStack base, Random random);
        }
    }
}
