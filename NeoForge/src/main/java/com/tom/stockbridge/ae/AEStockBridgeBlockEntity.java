package com.tom.stockbridge.ae;

import org.apache.commons.lang3.mutable.MutableBoolean;

import com.simibubi.create.api.packager.InventoryIdentifier;
import com.simibubi.create.content.logistics.packager.IdentifiedInventory;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.content.logistics.packager.PackagingRequest;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;
import com.tom.stockbridge.util.StockBridgeInventory;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import appeng.api.storage.StorageHelper;
import net.createmod.catnip.data.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemHandlerHelper;

public class AEStockBridgeBlockEntity extends AbstractAEStockBridgeBlockEntity {
	private StockBridgeInventory inv = new StockBridgeInventory(this);

	public AEStockBridgeBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	@Override
	public void tick() {
		super.tick();
		if (!level.isClientSide && !inv.isInsertEmpty()) {
			final IGrid grid = this.getMainNode().getGrid();
			if (grid != null) {
				final MEStorage networkInv = grid.getStorageService().getInventory();
				final IEnergyService energySrc = grid.getEnergyService();
				for (int slot = 0; slot < inv.insertW.getSlots(); slot++) {
					ItemStack is = inv.insertW.getStackInSlot(slot);
					if (!is.isEmpty()) {
						AEItemKey what = AEItemKey.of(is);
						final int inserted = (int) StorageHelper.poweredInsert(energySrc, networkInv, what,
								is.getCount(), actionSource);
						if (inserted > 0) {
							inv.insertW.extractItem(slot, inserted, false);
						}
					}
				}
			}
		}
	}

	@Override
	public Pair<PackagerBlockEntity, PackagingRequest> processRequest(ItemStack stack, int amount, String address,
			int linkIndex, MutableBoolean finalLink, int orderId, PackageOrderWithCrafts context) {
		final IGrid grid = this.getMainNode().getGrid();
		if (grid == null)
			return null;
		PackagerBlockEntity packager = getPackager();
		if (packager == null)
			return null;

		final MEStorage networkInv = grid.getStorageService().getInventory();
		final IEnergyService energySrc = grid.getEnergyService();

		var what = AEItemKey.of(stack);
		final long acquired = StorageHelper.poweredExtraction(energySrc, networkInv, what, amount, actionSource,
				Actionable.SIMULATE);

		var r = ItemHandlerHelper.insertItemStacked(inv.extractW, stack.copyWithCount((int) acquired), true);
		int count = (int) (acquired - r.getCount());

		if (acquired <= 0 || count <= 0) {
			return null;
		}

		return Pair.of(packager,
				PackagingRequest.create(stack, count, address, linkIndex, finalLink, 0, orderId, context));
	}

	@Override
	public long insertCraftedItems(ICraftingLink link, AEKey what, long amount, Actionable action) {
		if (what instanceof AEItemKey item) {
			ItemStack is = item.toStack((int) amount);
			ItemStack i = ItemHandlerHelper.insertItemStacked(inv.extractW, is, action == Actionable.MODULATE);
			return amount - i.getCount();
		}
		return 0;
	}

	@Override
	public IdentifiedInventory getInvId() {
		return new IdentifiedInventory(new InventoryIdentifier.Single(worldPosition), inv);
	}

	@Override
	public void pull(PackagingRequest packagingRequest) {
		final IGrid grid = this.getMainNode().getGrid();
		if (grid == null)
			return;
		final MEStorage networkInv = grid.getStorageService().getInventory();
		final IEnergyService energySrc = grid.getEnergyService();

		var what = AEItemKey.of(packagingRequest.item());

		long extracted = StorageHelper.poweredExtraction(energySrc, networkInv, what, packagingRequest.getCount(),
				actionSource);
		ItemStack ex = ItemHandlerHelper.insertItemStacked(inv.extractW,
				packagingRequest.item().copyWithCount((int) extracted), false);
		if (!ex.isEmpty()) {
			// TODO handle full
		}
		sendPulseNextSync();
		notifyUpdate();
	}

	@Override
	protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
		super.write(tag, registries, clientPacket);
		if (!clientPacket) {
			tag.put("inv", inv.write(registries));
		}
	}

	@Override
	protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
		super.read(tag, registries, clientPacket);
		if (!clientPacket) {
			inv.read(tag.getCompound("inv"), registries);
		}
	}

	public StockBridgeInventory getInv() {
		return inv;
	}
}
