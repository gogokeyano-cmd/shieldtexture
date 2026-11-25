package dev.autoshield;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

public class AutoShieldClient implements ClientModInitializer {
    // Ticks (20 = 1 second). Raise these if hits do not register on a laggy connection.
    private static final int TICKS_BEFORE_SWAP_BACK = 2;
    private static final int TICKS_BETWEEN_BREAKS = 10;

    private KeyMapping toggleKey;
    private boolean enabled = false; // off until you press the key
    private int previousSlot = -1;
    private int swapBackTimer = 0;
    private int cooldown = 0;

    @Override
    public void onInitializeClient() {
        KeyMapping.Category category = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath("autoshield", "main"));
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.autoshield.toggle", InputConstants.Type.KEYSYM, InputConstants.KEY_H, category));
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }

    private void tick(Minecraft client) {
        LocalPlayer player = client.player;
        if (player == null || client.level == null || client.gameMode == null) {
            previousSlot = -1;
            return;
        }

        while (toggleKey.consumeClick()) {
            enabled = !enabled;
            player.displayClientMessage(
                    Component.literal("Auto Shield Breaker: " + (enabled ? "ON" : "OFF")), true);
        }

        if (cooldown > 0) cooldown--;

        // Finish an earlier break first: go back to the sword.
        if (previousSlot != -1) {
            if (--swapBackTimer <= 0) {
                player.getInventory().setSelectedSlot(previousSlot);
                previousSlot = -1;
            }
            return;
        }

        if (!enabled || cooldown > 0 || client.screen != null) return;
        if (!player.isAlive() || player.isSpectator() || player.isUsingItem()) return;
        if (!player.getMainHandItem().is(ItemTags.SWORDS)) return;

        // Only a player under your crosshair and in reach counts. The mod does not aim for you.
        if (!(client.hitResult instanceof EntityHitResult hit)) return;
        if (!(hit.getEntity() instanceof Player target)) return;
        if (!target.isAlive() || !target.isBlocking() || !isFacing(target, player)) return;

        int axeSlot = findAxe(player.getInventory());
        if (axeSlot == -1) return;

        previousSlot = player.getInventory().getSelectedSlot();
        player.getInventory().setSelectedSlot(axeSlot);
        client.gameMode.attack(player, target); // also tells the server about the slot change
        player.swing(InteractionHand.MAIN_HAND);
        swapBackTimer = TICKS_BEFORE_SWAP_BACK;
        cooldown = TICKS_BETWEEN_BREAKS;
    }

    /** A shield only blocks from the front, so skip targets that are looking away from you. */
    private static boolean isFacing(Player target, Player me) {
        Vec3 toMe = me.position().subtract(target.position()).multiply(1, 0, 1);
        Vec3 look = Vec3.directionFromRotation(0, target.getYHeadRot());
        return look.dot(toMe) > 0;
    }

    private static int findAxe(Inventory inventory) {
        for (int slot = 0; slot < 9; slot++) {
            if (inventory.getItem(slot).is(ItemTags.AXES)) return slot;
        }
        return -1;
    }
}
