package net.samagames.survivalapi.game.types.team;

import net.minecraft.server.v1_8_R3.EntityHuman;
import net.minecraft.server.v1_8_R3.TileEntitySign;
import net.samagames.api.SamaGamesAPI;
import net.samagames.api.gui.AbstractGui;
import net.samagames.survivalapi.game.SurvivalTeam;
import net.samagames.survivalapi.game.types.SurvivalTeamGame;
import net.samagames.tools.chat.fanciful.FancyMessage;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.craftbukkit.v1_8_R3.block.CraftSign;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

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
public class GuiSelectTeam extends AbstractGui
{
    private static SurvivalTeamGame game;
    private static SurvivalTeamSelector selector = SurvivalTeamSelector.getInstance();
    private static int x = 0;

    private Field signField;
    private Field isEditable;
    private Method openSign;
    private Method setEditor;
    private Method getHandle;

    /**
     * Display the GUI
     *
     * @param player Player
     */
    @Override
    public void display(Player player)
    {
        this.inventory = Bukkit.getServer().createInventory(null, 54, "Sélection d'équipe");

        try
        {
            this.signField = CraftSign.class.getDeclaredField("sign");
            this.signField.setAccessible(true);
            this.isEditable = TileEntitySign.class.getDeclaredField("isEditable");
            this.isEditable.setAccessible(true);
            this.getHandle = CraftPlayer.class.getDeclaredMethod("getHandle");
            this.openSign = EntityHuman.class.getDeclaredMethod("openSign", TileEntitySign.class);
            this.setEditor = TileEntitySign.class.getDeclaredMethod("a", EntityHuman.class);
        }
        catch (NoSuchFieldException | SecurityException | NoSuchMethodException ex)
        {
            this.game.getPlugin().getLogger().log(Level.SEVERE, "Error patching NMS", ex);
        }

        int last = 10;

        for (SurvivalTeam team : game.getTeams())
        {
            String name = team.getChatColor() + "Equipe " + team.getTeamName() + " [" + team.getPlayersUUID().size() + "/" + game.getPersonsPerTeam() + "]";
            ArrayList<String> lores = new ArrayList<>();

            if (team.isLocked())
            {
                lores.add(ChatColor.RED + "L'équipe est fermée !");
                lores.add("");
            }

            for (UUID uuid : team.getPlayersUUID().keySet())
            {
                if (game.getPlugin().getServer().getPlayer(uuid) != null)
                    lores.add(team.getChatColor() + " - " + Bukkit.getPlayer(uuid).getName());
                else
                    team.removePlayer(uuid);
            }

            this.setSlotData(name, team.getIcon(), last, lores.toArray(new String[lores.size()]), "team_" + team.getChatColor());

            if (last == 16)
                last = 19;
            else
                last++;
        }

        this.setSlotData("Sortir de l'équipe", Material.ARROW, 31, null, "leave");

        String[] lores = new String[]{ChatColor.GREEN + "Réservé aux VIP :)"};

        this.setSlotData("Ouvrir/Fermer l'équipe", Material.BARRIER, 39, lores, "openclose");
        this.setSlotData("Changer le nom de l'équipe", Material.BOOK_AND_QUILL, 40, lores, "teamname");
        this.setSlotData("Inviter un joueur", Material.FEATHER, 41, lores, "invit");

        player.openInventory(this.inventory);
    }

    /**
     * Event fired when a player click into the GUI
     *
     * @param player Player
     * @param stack Stack
     * @param action Stack's defined action name
     */
    @Override
    public void onClick(final Player player, ItemStack stack, String action)
    {

        if (action.startsWith("team_"))
        {
            for (SurvivalTeam team : game.getTeams())
            {
                if (action.equals("team_" + team.getChatColor()))
                {
                    if (!team.isLocked())
                    {
                        if (team.canJoin())
                        {
                            if (game.getPlayerTeam(player.getUniqueId()) != null)
                                game.getPlayerTeam(player.getUniqueId()).removePlayer(player.getUniqueId());

                            team.join(player.getUniqueId());
                            player.sendMessage(game.getCoherenceMachine().getGameTag() + " " + ChatColor.YELLOW + "Vous êtes entré dans l'équipe " + team.getChatColor() + team.getTeamName() + ChatColor.YELLOW + " !");
                        }
                        else
                        {
                            player.sendMessage(game.getCoherenceMachine().getGameTag() + " " + ChatColor.RED + "L'équipe choisie est pleine.");
                        }
                    }
                    else
                    {
                        player.sendMessage(game.getCoherenceMachine().getGameTag() + " " + ChatColor.RED + "L'équipe choisie est fermée !");
                    }

                    break;
                }
            }

            selector.openGui(player, new GuiSelectTeam());
        }
        else if ("teamname".equals(action))
        {
            if (SamaGamesAPI.get().getPermissionsManager().hasPermission(player, "network.vip"))
            {
                if (game.getPlayerTeam(player.getUniqueId()) != null)
                {
                    final Block block = player.getWorld().getBlockAt(++x, 250, 150);
                    block.setTypeIdAndData(Material.SIGN_POST.getId(), (byte) 2, false);
                    block.getRelative(BlockFace.DOWN).setType(Material.BARRIER);
                    Sign sign = (Sign) block.getState();
                    sign.setLine(0, game.getPlayerTeam(player.getUniqueId()).getTeamName());
                    sign.update(true);

                    Bukkit.getScheduler().scheduleSyncDelayedTask(game.getPlugin(), () ->
                    {
                        try
                        {
                            final Object signTile = this.signField.get(block.getState());
                            final Object entityPlayer = this.getHandle.invoke(player);

                            Bukkit.getScheduler().scheduleSyncDelayedTask(game.getPlugin(), () ->
                            {
                                try
                                {
                                    this.openSign.invoke(entityPlayer, signTile);
                                    this.setEditor.invoke(signTile, entityPlayer);
                                    this.isEditable.set(signTile, true);
                                }
                                catch (IllegalArgumentException | InvocationTargetException | IllegalAccessException ex)
                                {
                                    game.getPlugin().getLogger().log(Level.SEVERE, "Reflection error", ex);
                                }
                            }, 5L);
                        }
                        catch (IllegalArgumentException | IllegalAccessException | InvocationTargetException ex)
                        {
                            game.getPlugin().getLogger().log(Level.SEVERE, "Reflection error", ex);
                        }
                    }, 1L);
                }
                else
                {
                    player.sendMessage(game.getCoherenceMachine().getGameTag() + " " + ChatColor.RED + "Vous devez avoir une équipe pour pouvoir utiliser cette fonction !");
                }
            }
            else
            {
                player.sendMessage(game.getCoherenceMachine().getGameTag() + " " + ChatColor.RED + "Vous devez être VIP pour pouvoir utiliser cette fonction !");
            }
        }
        else if ("openclose".equals(action))
        {
            if (SamaGamesAPI.get().getPermissionsManager().hasPermission(player, "network.vip"))
            {
                if (game.getPlayerTeam(player.getUniqueId()) != null)
                {
                    if (game.getPlayerTeam(player.getUniqueId()).isLocked())
                    {
                        game.getPlayerTeam(player.getUniqueId()).setLocked(false);
                        player.sendMessage(game.getCoherenceMachine().getGameTag() + " " + ChatColor.GREEN + "Votre équipe est maintenant ouverte !");
                    }
                    else
                    {
                        game.getPlayerTeam(player.getUniqueId()).setLocked(true);
                        player.sendMessage(game.getCoherenceMachine().getGameTag() + " " + ChatColor.RED + "Votre équipe est maintenant fermée !");
                    }
                }
                else
                {
                    player.sendMessage(game.getCoherenceMachine().getGameTag() + " " + ChatColor.RED + "Vous devez avoir une équipe pour pouvoir utiliser cette fonction !");
                }
            }
            else
            {
                player.sendMessage(game.getCoherenceMachine().getGameTag() + " " + ChatColor.RED + "Vous devez être VIP pour pouvoir utiliser cette fonction !");
            }
        }
        else if ("invit".equals(action))
        {
            if (SamaGamesAPI.get().getPermissionsManager().hasPermission(player, "network.vip"))
            {
                if (game.getPlayerTeam(player.getUniqueId()) != null)
                {
                    player.sendMessage(game.getCoherenceMachine().getGameTag() + " " + ChatColor.YELLOW + "Vous pouvez inviter les joueurs suivants :");

                    Set<UUID> uuids = (Set<UUID>) game.getInGamePlayers().keySet();

                    uuids.stream().filter(aInvite -> game.getPlayerTeam(aInvite) == null).filter(aInvite -> Bukkit.getPlayer(aInvite) != null).forEach(aInvite -> new FancyMessage(" - " + Bukkit.getPlayer(aInvite).getName() + " ")
                            .color(ChatColor.GRAY)
                            .then("[Inviter]")
                            .color(ChatColor.GREEN)
                            .style(ChatColor.BOLD)
                            .command("/uhc invite " + Bukkit.getPlayer(aInvite).getName())
                            .send(player));
                }
                else
                {
                    player.sendMessage(game.getCoherenceMachine().getGameTag() + " " + ChatColor.RED + "Vous devez avoir une équipe pour pouvoir utiliser cette fonction !");
                }
            }
            else
            {
                player.sendMessage(game.getCoherenceMachine().getGameTag() + " " + ChatColor.RED + "Vous devez être VIP pour pouvoir utiliser cette fonction !");
            }
        }
        else if ("leave".equals(action))
        {
            if (game.getPlayerTeam(player.getUniqueId()) != null)
            {
                game.getPlayerTeam(player.getUniqueId()).removePlayer(player.getUniqueId());
                player.sendMessage(game.getCoherenceMachine().getGameTag() + " " + ChatColor.GREEN + "Vous avez quitté l'équipe !");
            }
            else
            {
                player.sendMessage(game.getCoherenceMachine().getGameTag() + " " + ChatColor.RED + "Vous devez avoir une équipe pour pouvoir utiliser cette fonction !");
            }
        }
    }

    /**
     * Set game instance for future uses
     *
     * @param instance Game instance
     */
    public static void setGame(SurvivalTeamGame instance)
    {
        game = instance;
    }

    /**
     * Get the GUI inventory
     *
     * @return Instance
     */
    @Override
    public Inventory getInventory()
    {
        return this.inventory;
    }
}
