package com.example.inventorymod;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.SlotItemHandler;
import org.lwjgl.glfw.GLFW;

import java.util.concurrent.atomic.AtomicBoolean;

@Mod("inventorymod")
public class InventoryMod {

    // Define the KeyMapping 
    public static final KeyMapping TRANSFER_ITEMS_KEY = new KeyMapping(
            "key.inventorymod.transfer_items",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_R,
            "key.categories.inventory"
    );

    // Flag to track when the key was pressed (client side)
    private static final AtomicBoolean transferRequested = new AtomicBoolean(false);

    // Default constructor required by Forge
    public InventoryMod() {
        // Get the mod event bus directly from FMLJavaModLoadingContext
        // Use get() even though it's deprecated since there's no alternative in 1.21.5
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        
        // Register mod lifecycle events
        modEventBus.addListener(this::commonSetup);
        
        // Register the configuration with the current ModContainer
        ModList.get().getModContainerById("inventorymod").ifPresent(
            container -> ((ModContainer)container).addConfig(new ModConfig(
                ModConfig.Type.COMMON, 
                Config.COMMON_SPEC, 
                container
            ))
        );
        
        // Register the mod event bus subscriber
        modEventBus.register(this);
        
        // Register the Forge event bus subscribers
        MinecraftForge.EVENT_BUS.register(ForgeEvents.class);
        MinecraftForge.EVENT_BUS.register(ServerEvents.class);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        // No setup needed anymore since we don't use networking
    }

    // Register key on the mod event bus
    @Mod.EventBusSubscriber(modid = "inventorymod", bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ModEvents {
        @SubscribeEvent
        public static void registerKeys(RegisterKeyMappingsEvent event) {
            event.register(TRANSFER_ITEMS_KEY);
        }
    }

    // Handle key press events on client side
    @Mod.EventBusSubscriber(modid = "inventorymod", bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
    public static class ForgeEvents {
        @SubscribeEvent
        public static void onKeyInput(InputEvent.Key event) {
            // Only process on key press, not release or repeat
            if (event.getAction() != GLFW.GLFW_PRESS) {
                return;
            }
            
            // Check if the transfer key is pressed and the feature is enabled in config
            if (TRANSFER_ITEMS_KEY.isDown() && Config.enableItemTransfer.get()) {
                Minecraft mc = Minecraft.getInstance();
                // Only set the flag if player exists and no GUI screen is open
                if (mc.player != null && mc.screen == null) {
                    // Set flag for the server tick to process
                    transferRequested.set(true);
                }
            }
        }
    }

    // Handle server tick events to process inventory transfers
    @Mod.EventBusSubscriber(modid = "inventorymod", bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static class ServerEvents {
        @SubscribeEvent
        public static void onServerTick(TickEvent.PlayerTickEvent event) {
            // Only process on server side and at end of tick phase
            if (event.phase != TickEvent.Phase.END) {
                return;
            }

            Player player = event.player;
            // Check if this is the player who requested a transfer
            if (player instanceof ServerPlayer && transferRequested.compareAndSet(true, false)) {
                // Perform the transfer
                transferPlayerInventoryToContainer(player);
            }
        }
    }

    // Method to perform the inventory transfer
    private static void transferPlayerInventoryToContainer(Player player) {
        AbstractContainerMenu currentMenu = player.containerMenu;
        if (currentMenu == null) return; // No container open

        // Find the container's inventory handler
        for (Slot slot : currentMenu.slots) {
            // Check if the slot is not from player inventory and is a SlotItemHandler
            if (!(slot.container instanceof net.minecraft.world.entity.player.Inventory) 
                    && slot instanceof SlotItemHandler slotItemHandler) {
                // Using a final variable to avoid lambda issues
                final IItemHandler targetHandler = slotItemHandler.getItemHandler();
                
                // If we found a valid inventory handler for the container
                if (targetHandler != null) {
                    player.getCapability(ForgeCapabilities.ITEM_HANDLER).ifPresent(playerInventoryHandler -> {
                        // Define playerInvSlots here inside the lambda
                        int playerInvSlots = 36; // Standard inventory size (hotbar + main inventory)
                        boolean transferred = false;

                        for (int i = 0; i < playerInvSlots; ++i) {
                            ItemStack stackInPlayer = playerInventoryHandler.getStackInSlot(i);
                            if (!stackInPlayer.isEmpty()) {
                                // Try to insert the item into the container
                                ItemStack remainder = ItemHandlerHelper.insertItemStacked(
                                        targetHandler, stackInPlayer.copy(), true);
                                
                                // Calculate how much can actually be inserted
                                int insertableAmount = stackInPlayer.getCount() - remainder.getCount();
                                
                                if (insertableAmount > 0) {
                                    // Extract that amount from player inventory
                                    ItemStack extracted = playerInventoryHandler.extractItem(i, insertableAmount, false);
                                    // Insert it into the container
                                    ItemHandlerHelper.insertItemStacked(targetHandler, extracted, false);
                                    transferred = true;
                                }
                            }
                        }
                        
                        // Update the container state if any items were transferred
                        if (transferred) {
                            currentMenu.broadcastChanges();
                        }
                    });
                }
                
                // We've processed the first non-player inventory slot, so we can break the loop
                break;
            }
        }
    }
}
