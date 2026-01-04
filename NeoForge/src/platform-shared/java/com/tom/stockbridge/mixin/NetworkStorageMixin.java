package com.tom.stockbridge.mixin;

import java.util.HashSet;
import java.util.List;
import java.util.NavigableMap;
import java.util.Set;
import java.util.UUID;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.tom.stockbridge.ae.AbstractAEStockBridgeBlockEntity;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import appeng.me.storage.NetworkStorage;

@Mixin(value = NetworkStorage.class, remap = false)
public class NetworkStorageMixin {
  @Shadow
  private NavigableMap<Integer, List<MEStorage>> priorityInventory;

  @Inject(method = "insert", at = @At("RETURN"))
  @SuppressWarnings("unused")
  private void stockbridge_onInsert(AEKey what, long amount, Actionable type, IActionSource src,
      CallbackInfoReturnable<Long> cir) {
    if (type == Actionable.MODULATE && cir.getReturnValue() > 0) {
      // skip things that are manually inserted to the network storage (since they are
      // probably not promises being resolved)
      if (src.player().isPresent()) {
        return;
      }

      Set<UUID> notifiedNetworks = new HashSet<>();
      for (var entry : priorityInventory.entrySet()) {
        for (var storage : entry.getValue()) {
          if (storage instanceof AbstractAEStockBridgeBlockEntity.BridgeStorage bridgeStorage) {
            AbstractAEStockBridgeBlockEntity bridge = bridgeStorage.getBridgeBlockEntity();
            UUID freqId = bridge.behaviour.freqId;

            // Only notify once per Create logistics network
            if (notifiedNetworks.add(freqId)) {
              bridge.onItemInserted(what, amount);
            }
          }
        }
      }
    }
  }
}
