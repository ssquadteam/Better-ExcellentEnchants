package su.nightexpress.excellentenchants.enchantment.tool;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import su.nightexpress.excellentenchants.EnchantsPlugin;
import su.nightexpress.excellentenchants.api.EnchantData;
import su.nightexpress.excellentenchants.api.EnchantPriority;
import su.nightexpress.excellentenchants.api.enchantment.component.EnchantComponent;
import su.nightexpress.excellentenchants.api.enchantment.meta.Probability;
import su.nightexpress.excellentenchants.api.enchantment.type.InteractEnchant;
import su.nightexpress.excellentenchants.api.enchantment.type.MiningEnchant;
import su.nightexpress.excellentenchants.enchantment.GameEnchantment;
import su.nightexpress.nightcore.config.ConfigValue;
import su.nightexpress.nightcore.config.FileConfig;
import su.nightexpress.nightcore.util.bukkit.NightSound;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class ReplanterEnchant extends GameEnchantment implements InteractEnchant, MiningEnchant {

    private boolean replantOnRightClick;
    private boolean replantOnPlantBreak;

    private static final Map<Material, Material> CROP_MAP = new HashMap<>();

    static {
        CROP_MAP.put(Material.WHEAT_SEEDS, Material.WHEAT);
        CROP_MAP.put(Material.BEETROOT_SEEDS, Material.BEETROOTS);
        CROP_MAP.put(Material.MELON_SEEDS, Material.MELON_STEM);
        CROP_MAP.put(Material.PUMPKIN_SEEDS, Material.PUMPKIN_STEM);
        CROP_MAP.put(Material.POTATO, Material.POTATOES);
        CROP_MAP.put(Material.CARROT, Material.CARROTS);
        CROP_MAP.put(Material.NETHER_WART, Material.NETHER_WART);
    }

    public ReplanterEnchant(@NotNull EnchantsPlugin plugin, @NotNull File file, @NotNull EnchantData data) {
        super(plugin, file, data);
        this.addComponent(EnchantComponent.PROBABILITY, Probability.oneHundred());
    }

    @Override
    protected void loadAdditional(@NotNull FileConfig config) {
        this.replantOnRightClick = ConfigValue.create("Replanter.On_Right_Click",
            true,
            "When 'true', player will be able to replant crops when right-clicking farmland blocks."
        ).read(config);

        this.replantOnPlantBreak = ConfigValue.create("Replanter.On_Plant_Break",
            true,
            "When 'true', crops will be automatically replanted when player break plants with enchanted tool in hand."
        ).read(config);
    }

    public boolean isReplantOnPlantBreak() {
        return replantOnPlantBreak;
    }

    public boolean isReplantOnRightClick() {
        return replantOnRightClick;
    }

    private boolean takeSeeds(@NotNull Player player, @NotNull Material material) {
        int slot = player.getInventory().first(material);
        if (slot < 0) return false;

        ItemStack seed = player.getInventory().getItem(slot);
        if (seed == null || seed.getType().isAir()) return false;

        seed.setAmount(seed.getAmount() - 1);
        return true;
    }

    @NotNull
    @Override
    public EnchantPriority getInteractPriority() {
        return EnchantPriority.HIGHEST;
    }

    @Override
    @NotNull
    public EnchantPriority getBreakPriority() {
        return EnchantPriority.NORMAL;
    }

    @Override
    public boolean onInteract(@NotNull PlayerInteractEvent event, @NotNull LivingEntity entity, @NotNull ItemStack item, int level) {
        if (!(entity instanceof Player player)) return false;
        if (!this.isReplantOnRightClick()) return false;

        // Check for a event hand. We dont want to trigger it twice.
        if (event.getHand() != EquipmentSlot.HAND) return false;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return false;

        // Check if player holds seeds to plant them by offhand interaction.
        ItemStack off = player.getInventory().getItemInOffHand();
        if (!off.getType().isAir() && CROP_MAP.containsKey(off.getType())) return false;

        // Check if clicked block is a farmland.
        Block blockGround = event.getClickedBlock();
        if (blockGround == null) return false;
        if (blockGround.getType() != Material.FARMLAND && blockGround.getType() != Material.SOUL_SAND) return false;

        // Check if someting is already growing on the farmland.
        Block blockPlant = blockGround.getRelative(BlockFace.UP);
        if (!blockPlant.isEmpty()) return false;

        // Get the first crops from player's inventory and plant them.
        for (var entry : CROP_MAP.entrySet()) {
            Material seed = entry.getKey();
            if (seed == Material.NETHER_WART && blockGround.getType() == Material.SOUL_SAND
                || seed != Material.NETHER_WART && blockGround.getType() == Material.FARMLAND) {
                if (this.takeSeeds(player, seed)) {
                    NightSound.of(seed == Material.NETHER_WART ? Sound.ITEM_NETHER_WART_PLANT : Sound.ITEM_CROP_PLANT).play(player);
                    player.swingMainHand();
                    this.plugin.runAtLocation(blockPlant.getLocation(), () -> blockPlant.setType(entry.getValue()));
                    break;
                }
            }
        }
        return true;
    }

    @Override
    public boolean onBreak(@NotNull BlockBreakEvent event, @NotNull LivingEntity entity, @NotNull ItemStack item, int level) {
        if (!(entity instanceof Player player)) return false;
        if (!this.isReplantOnPlantBreak()) return false;

        Block blockPlant = event.getBlock();

        // Check if broken block is supported crop(s).
        if (!CROP_MAP.containsKey(blockPlant.getBlockData().getPlacementMaterial())) return false;

        // Check if broken block is actually can grow.
        BlockData dataPlant = blockPlant.getBlockData();
        if (!(dataPlant instanceof Ageable plant)) return false;

        // Replant the gathered crops with a new one.
        if (this.takeSeeds(player, dataPlant.getPlacementMaterial())) {
            this.plugin.runAtLocation(blockPlant.getLocation(), () -> {
                blockPlant.setType(plant.getMaterial());
                plant.setAge(0);
                blockPlant.setBlockData(plant);
            });
        }
        return true;
    }
}
