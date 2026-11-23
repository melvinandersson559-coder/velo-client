package com.velo.client;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.DeathScreen;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;

import java.util.Set;

public class VeloUtilityModules {

    // Fullbright
    public static boolean fullbright = true;
    private static boolean appliedNightVision = false;

    // ChestCleaner & ChestStealer
    public static boolean chestCleaner = false;
    public static boolean chestStealer = false;
    public static int chestDelayTicks = 2;
    private static int chestTickCounter = 0;

    // Player Utilities
    public static boolean autoTool = false;
    public static boolean autoReplenish = false;
    public static boolean autoRespawn = false;
    public static boolean antiAfk = false;
    public static boolean middleClickPearl = false;

    // Smooth Zoom
    public static boolean zoomEnabled = true;
    public static int zoomFov = 32;
    private static boolean wasZooming = false;
    private static int savedFov = 90;

    // AntiAFK & MCP state
    private static int afkTicks = 0;
    private static boolean middleWasDown = false;

    // Junk items som ChestCleaner rensar bort
    private static final Set<Item> JUNK_ITEMS = Set.of(
            Items.COBBLESTONE, Items.COBBLED_DEEPSLATE, Items.DIRT, Items.GRAVEL,
            Items.ANDESITE, Items.DIORITE, Items.GRANITE, Items.TUFF,
            Items.ROTTEN_FLESH, Items.SPIDER_EYE, Items.POISONOUS_POTATO,
            Items.WHEAT_SEEDS, Items.NETHERRACK, Items.ROTTEN_FLESH.asItem()
    );

    public static void tick(MinecraftClient mc, boolean zoomPressed) {
        if (mc.player == null || mc.world == null) {
            appliedNightVision = false;
            return;
        }

        // 1. FULLBRIGHT (Tyst client-side Night Vision utan partiklar/ikon)
        if (fullbright) {
            StatusEffectInstance current = mc.player.getStatusEffect(StatusEffects.NIGHT_VISION);
            if (current == null || current.getDuration() < 260) {
                mc.player.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 400, 0, false, false, false));
                appliedNightVision = true;
            }
        } else if (appliedNightVision) {
            mc.player.removeStatusEffect(StatusEffects.NIGHT_VISION);
            appliedNightVision = false;
        }

        // 2. SMOOTH ZOOM (Håll inne C)
        if (zoomEnabled && zoomPressed && mc.currentScreen == null) {
            if (!wasZooming) {
                savedFov = mc.options.getFov().getValue();
                wasZooming = true;
            }
            int cur = mc.options.getFov().getValue();
            int next = cur + (int) Math.round((zoomFov - cur) * 0.45);
            if (Math.abs(next - zoomFov) <= 2) next = zoomFov;
            mc.options.getFov().setValue(Math.clamp(next, 30, 110));
        } else if (wasZooming) {
            int cur = mc.options.getFov().getValue();
            int next = cur + (int) Math.round((savedFov - cur) * 0.45);
            if (Math.abs(next - savedFov) <= 2) {
                mc.options.getFov().setValue(Math.clamp(savedFov, 30, 110));
                wasZooming = false;
            } else {
                mc.options.getFov().setValue(Math.clamp(next, 30, 110));
            }
        }

        // 3. AUTO RESPAWN + Dödskoordinater i topp-popup
        if (autoRespawn && mc.currentScreen instanceof DeathScreen) {
            int dx = mc.player.getBlockX();
            int dy = mc.player.getBlockY();
            int dz = mc.player.getBlockZ();
            mc.player.requestRespawn();
            mc.setScreen(null);
            VeloWorldFeatures.showPopup(
                    "AUTO RESPAWN",
                    "DU DOG — ÅTERUPPLIVAD",
                    "Dina dödskoordinater sparades här",
                    "X: " + dx + "  Y: " + dy + "  Z: " + dz,
                    0xFFFF4D6D
            );
        }

        // 4. CHEST CLEANER & CHEST STEALER
        if ((chestCleaner || chestStealer) && mc.player.currentScreenHandler instanceof GenericContainerScreenHandler handler
                && mc.interactionManager != null) {
            chestTickCounter++;
            if (chestTickCounter >= Math.max(1, chestDelayTicks)) {
                chestTickCounter = 0;
                int containerSlots = handler.getRows() * 9;

                if (chestCleaner) {
                    // Kastar ut skräp-items (cobblestone, dirt, rotten flesh m.m.) ur kistan
                    boolean cleaned = false;
                    for (int i = 0; i < containerSlots; i++) {
                        ItemStack st = handler.getSlot(i).getStack();
                        if (!st.isEmpty() && isJunk(st)) {
                            mc.interactionManager.clickSlot(handler.syncId, i, 1, SlotActionType.THROW, mc.player);
                            cleaned = true;
                            break;
                        }
                    }
                    if (!cleaned && chestStealer) {
                        stealNextValuable(mc, handler, containerSlots);
                    }
                } else if (chestStealer) {
                    stealNextValuable(mc, handler, containerSlots);
                }
            }
        }

        // 5. AUTO TOOL (Byter automatiskt till bästa verktyget i hotbaren när du hugger)
        if (autoTool && mc.currentScreen == null && mc.options.attackKey.isPressed()
                && mc.crosshairTarget instanceof BlockHitResult bhr && bhr.getType() == HitResult.Type.BLOCK) {
            BlockState state = mc.world.getBlockState(bhr.getBlockPos());
            if (!state.isAir()) {
                int bestSlot = mc.player.getInventory().getSelectedSlot();
                float bestSpeed = mc.player.getInventory().getStack(bestSlot).getMiningSpeedMultiplier(state);
                for (int i = 0; i < 9; i++) {
                    ItemStack st = mc.player.getInventory().getStack(i);
                    float speed = st.getMiningSpeedMultiplier(state);
                    if (speed > bestSpeed) {
                        bestSpeed = speed;
                        bestSlot = i;
                    }
                }
                if (bestSlot != mc.player.getInventory().getSelectedSlot()) {
                    mc.player.getInventory().setSelectedSlot(bestSlot);
                }
            }
        }

        // 6. AUTO REPLENISH (Fyller på hotbar-slot från inventoryt när det är <= 8 kvar)
        if (autoReplenish && mc.currentScreen == null && mc.interactionManager != null) {
            int sel = mc.player.getInventory().getSelectedSlot();
            ItemStack main = mc.player.getInventory().getStack(sel);
            if (!main.isEmpty() && main.isStackable() && main.getCount() <= 8 && main.getCount() < main.getMaxCount()) {
                for (int i = 9; i < 36; i++) {
                    ItemStack invStack = mc.player.getInventory().getStack(i);
                    if (!invStack.isEmpty() && ItemStack.areItemsAndComponentsEqual(main, invStack)) {
                        mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, i, 0, SlotActionType.QUICK_MOVE, mc.player);
                        break;
                    }
                }
            }
        }

        // 7. MIDDLE CLICK PEARL (Kastar direkt Ender Pearl från hotbaren med mushjulet)
        if (middleClickPearl && mc.currentScreen == null && mc.interactionManager != null) {
            boolean down = mc.options.pickItemKey.isPressed();
            if (down && !middleWasDown) {
                int pearlSlot = -1;
                for (int i = 0; i < 9; i++) {
                    if (mc.player.getInventory().getStack(i).isOf(Items.ENDER_PEARL)) {
                        pearlSlot = i;
                        break;
                    }
                }
                if (pearlSlot != -1) {
                    int prev = mc.player.getInventory().getSelectedSlot();
                    mc.player.getInventory().setSelectedSlot(pearlSlot);
                    mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                    mc.player.getInventory().setSelectedSlot(prev);
                }
            }
            middleWasDown = down;
        }

        // 8. ANTI-AFK (Subtil rörelse var 5:e sekund så du inte blir kickad)
        if (antiAfk && mc.currentScreen == null) {
            afkTicks++;
            if (afkTicks >= 100) {
                afkTicks = 0;
                mc.player.setYaw(mc.player.getYaw() + 1.5f);
                if (mc.player.isOnGround()) {
                    mc.player.jump();
                }
            }
        }
    }

    private static void stealNextValuable(MinecraftClient mc, GenericContainerScreenHandler handler, int containerSlots) {
        for (int i = 0; i < containerSlots; i++) {
            ItemStack st = handler.getSlot(i).getStack();
            if (!st.isEmpty() && (!chestCleaner || !isJunk(st))) {
                mc.interactionManager.clickSlot(handler.syncId, i, 0, SlotActionType.QUICK_MOVE, mc.player);
                break;
            }
        }
    }

    private static boolean isJunk(ItemStack stack) {
        return JUNK_ITEMS.contains(stack.getItem());
    }
}
