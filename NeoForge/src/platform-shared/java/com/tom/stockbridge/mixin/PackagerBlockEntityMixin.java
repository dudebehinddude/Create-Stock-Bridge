package com.tom.stockbridge.mixin;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.content.logistics.packager.PackagingRequest;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.inventory.InvManipulationBehaviour;
import com.tom.stockbridge.ae.AEStockBridgeBlock;
import com.tom.stockbridge.block.entity.AbstractStockBridgeBlockEntity;
import com.tom.stockbridge.block.entity.AbstractStockBridgeBlockEntity.BridgeInventory;

import net.createmod.catnip.data.Iterate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

@Mixin(value = PackagerBlockEntity.class, remap = false)
public abstract class PackagerBlockEntityMixin extends SmartBlockEntity {
	private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(PackagerBlockEntityMixin.class);

	public @Shadow InvManipulationBehaviour targetInventory;

	public PackagerBlockEntityMixin(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	@Inject(at = @At("HEAD"), method = "getLinkPos", cancellable = true)
	private void stockbridge_onGetLinkPos(CallbackInfoReturnable<BlockPos> cbi) {
		for (Direction d : Iterate.directions) {
			BlockState adjacentState = level.getBlockState(worldPosition.relative(d));
			if (adjacentState.getBlock() instanceof AEStockBridgeBlock) {
				cbi.setReturnValue(worldPosition.relative(d));
				return;
			}
		}
	}

	/*
	 * @Inject(at = @At("HEAD"), method = "flashLink")
	 * private void stockbridge_onFlashLink(CallbackInfo cbi) {
	 * for (Direction d : Iterate.directions) {
	 * BlockState adjacentState = level.getBlockState(worldPosition.relative(d));
	 * if (adjacentState.getBlock() instanceof AEStockBridgeBlock) {
	 * WiFiEffectPacket.send(level, worldPosition.relative(d));
	 * return;
	 * }
	 * }
	 * }
	 */

	@Inject(at = @At("HEAD"), method = "attemptToSend")
	private void stockbridge_onAttemptToSend(List<PackagingRequest> queuedRequests, CallbackInfo cbi) {
		IItemHandler targetInv = targetInventory.getInventory();

		if (!(targetInv instanceof BridgeInventory bi))
			return;
		if (queuedRequests == null || queuedRequests.isEmpty())
			return;

		AbstractStockBridgeBlockEntity bridge = bi.getBlockEntity();

		PackagingRequest firstRequest = queuedRequests.get(0);
		String firstAddress = firstRequest.address();
		int firstOrderId = firstRequest.orderId();

		// Get all requests that can be combined (same address/orderId)
		List<PackagingRequest> combinedRequests = new ArrayList<>();
		Set<Item> combinedRequestItems = new HashSet<>();
		for (PackagingRequest request : queuedRequests) {
			if (!request.address().equals(firstAddress) || request.orderId() != firstOrderId) {
				break;
			}

			combinedRequests.add(request);
			combinedRequestItems.add(request.item().getItem());
		}

		var inv = bridge.getInv();

		// Return leftover items that aren't needed
		if (!inv.extractW.getInv().isEmpty()) {
			for (int i = 0; i < inv.extractW.getSlots(); i++) {
				ItemStack stack = inv.extractW.getStackInSlot(i);
				if (!stack.isEmpty() && !combinedRequestItems.contains(stack.getItem())) {
					LOGGER.info("Returning leftover item in packager inventory to ME: " + stack.getCount() + "x "
							+ stack.getDisplayName().getString());
					ItemStack notInserted = ItemHandlerHelper.insertItemStacked(inv.insertW, stack.copy(), false);
					inv.extractW.getInv().setItem(i, notInserted);
				}
			}
		}

		for (PackagingRequest request : combinedRequests) {
			// this will pull more than it needs to for large requests but it (should?) fix
			// itself since items not part of the current request end up getting returned so
			// I think it's fine?
			bridge.pull(request);
		}
	}
}
