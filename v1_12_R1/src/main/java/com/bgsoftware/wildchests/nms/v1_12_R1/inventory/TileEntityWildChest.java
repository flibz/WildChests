package com.bgsoftware.wildchests.nms.v1_12_R1.inventory;

import com.bgsoftware.wildchests.WildChestsPlugin;
import com.bgsoftware.wildchests.api.objects.chests.Chest;
import com.bgsoftware.wildchests.api.objects.chests.StorageChest;
import com.bgsoftware.wildchests.api.objects.data.ChestData;
import com.bgsoftware.wildchests.nms.v1_12_R1.NMSInventory;
import com.bgsoftware.wildchests.objects.chests.WChest;
import com.bgsoftware.wildchests.objects.chests.WStorageChest;
import com.bgsoftware.wildchests.objects.containers.TileEntityContainer;
import com.bgsoftware.wildchests.objects.inventory.WildItemStack;
import com.bgsoftware.wildchests.utils.ChestUtils;
import com.google.common.base.Predicate;
import net.minecraft.server.v1_12_R1.AxisAlignedBB;
import net.minecraft.server.v1_12_R1.Block;
import net.minecraft.server.v1_12_R1.BlockPosition;
import net.minecraft.server.v1_12_R1.Blocks;
import net.minecraft.server.v1_12_R1.ChatComponentText;
import net.minecraft.server.v1_12_R1.Container;
import net.minecraft.server.v1_12_R1.Entity;
import net.minecraft.server.v1_12_R1.EntityHuman;
import net.minecraft.server.v1_12_R1.EntityItem;
import net.minecraft.server.v1_12_R1.EnumDirection;
import net.minecraft.server.v1_12_R1.EnumParticle;
import net.minecraft.server.v1_12_R1.IChatBaseComponent;
import net.minecraft.server.v1_12_R1.ITickable;
import net.minecraft.server.v1_12_R1.IWorldInventory;
import net.minecraft.server.v1_12_R1.ItemStack;
import net.minecraft.server.v1_12_R1.NBTTagCompound;
import net.minecraft.server.v1_12_R1.NonNullList;
import net.minecraft.server.v1_12_R1.PlayerInventory;
import net.minecraft.server.v1_12_R1.SoundCategory;
import net.minecraft.server.v1_12_R1.SoundEffect;
import net.minecraft.server.v1_12_R1.SoundEffects;
import net.minecraft.server.v1_12_R1.TileEntity;
import net.minecraft.server.v1_12_R1.TileEntityChest;
import net.minecraft.server.v1_12_R1.World;
import net.minecraft.server.v1_12_R1.WorldServer;
import org.bukkit.GameMode;
import org.bukkit.Particle;
import org.bukkit.craftbukkit.v1_12_R1.CraftParticle;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftHumanEntity;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftItem;
import org.bukkit.craftbukkit.v1_12_R1.event.CraftEventFactory;
import org.bukkit.craftbukkit.v1_12_R1.inventory.CraftItemStack;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Item;

import java.util.List;

public class TileEntityWildChest extends TileEntityChest implements IWorldInventory, TileEntityContainer, ITickable {

    private static final WildChestsPlugin plugin = WildChestsPlugin.getPlugin();

    private final TileEntityChest tileEntityChest = new TileEntityChest();
    private final Chest chest;
    private final boolean isTrappedChest;

    private short currentCooldown = ChestUtils.DEFAULT_COOLDOWN;

    private AxisAlignedBB suctionItems = null;
    private boolean autoCraftMode = false;
    private boolean autoSellMode = false;

    public TileEntityWildChest(Chest chest, World world, BlockPosition blockPosition) {
        this.chest = chest;
        this.world = world;
        updateTile(this, world, blockPosition);
        updateTile(tileEntityChest, world, blockPosition);
        isTrappedChest = world.getType(blockPosition).getBlock() == Blocks.TRAPPED_CHEST;
        ((WChest) chest).setTileEntityContainer(this);
        updateData();
    }

    @Override
    protected NonNullList<ItemStack> q() {
        return getContents();
    }

    @Override
    public void setItem(int i, ItemStack itemStack) {
        ((WChest) chest).setItem(i, new WildItemStack<>(itemStack, CraftItemStack.asCraftMirror(itemStack)));
    }

    @Override
    public ItemStack getItem(int i) {
        return (ItemStack) ((WChest) chest).getWildItem(i).getItemStack();
    }

    @Override
    public NonNullList<ItemStack> getContents() {
        WildItemStack<?, ?>[] contents = ((WChest) chest).getWildContents();
        NonNullList<ItemStack> nonNullList = NonNullList.a(contents.length, ItemStack.a);

        for (int i = 0; i < contents.length; i++)
            nonNullList.set(i, (ItemStack) contents[i].getItemStack());

        return nonNullList;
    }

    @Override
    public NBTTagCompound save(NBTTagCompound nbttagcompound) {
        return tileEntityChest.save(nbttagcompound);
    }

    @Override
    public NBTTagCompound d() {
        return save(new NBTTagCompound());
    }

    @Override
    public String getContainerName() {
        return chest instanceof StorageChest ? "minecraft:hopper" : super.getContainerName();
    }

    @Override
    public Container createContainer(PlayerInventory playerinventory, EntityHuman entityHuman) {
        Container container = NMSInventory.createContainer(playerinventory, entityHuman, (com.bgsoftware.wildchests.objects.inventory.CraftWildInventory) chest.getPage(0));
        startOpen(playerinventory.player);
        return container;
    }

    @Override
    public IChatBaseComponent getScoreboardDisplayName() {
        return new ChatComponentText(chest.getPage(0).getTitle());
    }

    @Override
    public final void closeContainer(EntityHuman entityHuman) {
        CraftHumanEntity craftHumanEntity = entityHuman.getBukkitEntity();

        this.l = (int) this.transaction.stream().filter(human -> human.getGameMode() != GameMode.SPECTATOR).count();
        this.transaction.remove(craftHumanEntity);

        if (!craftHumanEntity.getHandle().isSpectator()) {
            int oldPower = Math.max(0, Math.min(15, this.l));
            this.l--;

            this.world.playBlockAction(this.position, this.getBlock(), 1, this.l);
            this.world.applyPhysics(this.position, this.getBlock(), false);

            if (isTrappedChest) {
                int newPower = Math.max(0, Math.min(15, this.l));
                if (oldPower != newPower) {
                    CraftEventFactory.callRedstoneChange(this.world, this.position.getX(), this.position.getY(), this.position.getZ(), oldPower, newPower);
                }

                this.world.applyPhysics(this.position.down(), this.getBlock(), false);
            }

            if (l <= 0)
                playOpenSound(SoundEffects.aa);
        }
    }

    @Override
    public final void startOpen(EntityHuman entityHuman) {
        CraftHumanEntity craftHumanEntity = entityHuman.getBukkitEntity();

        this.transaction.add(craftHumanEntity);

        if (!craftHumanEntity.getHandle().isSpectator()) {
            if (this.l < 0) {
                this.l = 0;
            }

            int oldPower = Math.max(0, Math.min(15, this.l));
            this.l++;
            if (this.world == null) {
                return;
            }

            this.world.playBlockAction(this.position, this.getBlock(), 1, this.l);

            if (isTrappedChest) {
                int newPower = Math.max(0, Math.min(15, this.l));
                if (oldPower != newPower) {
                    CraftEventFactory.callRedstoneChange(this.world, this.position.getX(), this.position.getY(), this.position.getZ(), oldPower, newPower);
                }
            }

            this.world.applyPhysics(this.position, this.getBlock(), false);

            if (isTrappedChest)
                this.world.applyPhysics(this.position.down(), this.getBlock(), false);

            if (l == 1)
                playOpenSound(SoundEffects.ac);
        }
    }

    @Override
    public final void onOpen(CraftHumanEntity who) {

    }

    @Override
    public final void onClose(CraftHumanEntity who) {

    }

    @Override
    public void e() {
        super.e();

        ChestData chestData = chest.getData();

        {
            double x = position.getX() + world.random.nextFloat();
            double y = position.getY() + world.random.nextFloat();
            double z = position.getZ() + world.random.nextFloat();
            for (String particle : chestData.getChestParticles()) {
                try {
                    ((WorldServer) world).sendParticles(null, EnumParticle.valueOf(particle),
                            false, x, y, z, 0, 0.0, 0.0, 0.0, 1.0);
                } catch (Exception ignored) {
                }
            }
        }

        if (--currentCooldown >= 0)
            return;

        Block currentBlock = world.getType(position).getBlock();

        if (((WChest) chest).isRemoved() || (currentBlock != Blocks.CHEST && currentBlock != Blocks.TRAPPED_CHEST)) {
            world.b(this);
            return;
        }

        currentCooldown = ChestUtils.DEFAULT_COOLDOWN;

        if (suctionItems != null) {
            for (Entity entity : world.a(EntityItem.class, suctionItems, (Predicate<? super EntityItem>) entity ->
                    entity != null && ChestUtils.SUCTION_PREDICATE.test((CraftItem) entity.getBukkitEntity(), chestData))) {
                EntityItem entityItem = (EntityItem) entity;
                org.bukkit.inventory.ItemStack itemStack = CraftItemStack.asCraftMirror(entityItem.getItemStack());
                Item item = (Item) entityItem.getBukkitEntity();

                itemStack.setAmount(plugin.getProviders().getItemAmount(item));

                org.bukkit.inventory.ItemStack remainingItem = ChestUtils.getRemainingItem(chest.addItems(itemStack));

                if (remainingItem == null) {
                    ((WorldServer) world).sendParticles(null, CraftParticle.toNMS(Particle.CLOUD), false,
                            entityItem.locX, entityItem.locY, entityItem.locZ, 0, 0.0, 0.0, 0.0, 1.0);
                    entityItem.die();
                } else {
                    plugin.getProviders().setItemAmount(item, remainingItem.getAmount());
                }
            }
        }

        if (autoCraftMode) {
            ChestUtils.tryCraftChest(chest);
        }

        if (autoSellMode) {
            ChestUtils.trySellChest(chest);
        }
    }

    @Override
    public int getSize() {
        return chest.getPage(0).getSize();
    }

    @Override
    public int getViewingCount() {
        if (this.l < 0)
            this.l = 0;

        return l;
    }

    @Override
    public List<HumanEntity> getTransaction() {
        return transaction;
    }

    @Override
    public void updateData() {
        ChestData chestData = chest.getData();
        suctionItems = !chestData.isAutoSuction() ? null : new AxisAlignedBB(
                chestData.isAutoSuctionChunk() ? position.getX() >> 4 << 4 : position.getX() - chestData.getAutoSuctionRange(),
                position.getY() - chestData.getAutoSuctionRange(),
                chestData.isAutoSuctionChunk() ? position.getZ() >> 4 << 4 : position.getZ() - chestData.getAutoSuctionRange(),
                chestData.isAutoSuctionChunk() ? (position.getX() >> 4 << 4) + 16 : position.getX() + chestData.getAutoSuctionRange(),
                position.getY() + chestData.getAutoSuctionRange(),
                chestData.isAutoSuctionChunk() ? (position.getZ() >> 4 << 4) + 16 : position.getZ() + chestData.getAutoSuctionRange()
        );
        autoCraftMode = chestData.isAutoCrafter();
        autoSellMode = chestData.isSellMode();
    }

    @Override
    public int[] getSlotsForFace(EnumDirection enumDirection) {
        return chest.getSlotsForFace();
    }

    @Override
    public boolean canPlaceItemThroughFace(int i, ItemStack itemStack, EnumDirection enumDirection) {
        return chest.canPlaceItemThroughFace(CraftItemStack.asCraftMirror(itemStack));
    }

    @Override
    public boolean canTakeItemThroughFace(int i, ItemStack itemStack, EnumDirection enumDirection) {
        return chest.canTakeItemThroughFace(i, CraftItemStack.asCraftMirror(itemStack));
    }

    @Override
    public ItemStack splitStack(int slot, int amount) {
        return slot != -2 || !(chest instanceof StorageChest) ? super.splitStack(slot, amount) :
                (ItemStack) ((WStorageChest) chest).splitItem(amount).getItemStack();
    }

    @Override
    public void update() {
        super.update();
        if (chest instanceof StorageChest)
            ((StorageChest) chest).update();
    }

    private void updateTile(TileEntity tileEntity, World world, BlockPosition blockPosition) {
        tileEntity.a(world);
        tileEntity.setPosition(blockPosition);
    }

    private void playOpenSound(SoundEffect soundEffect) {
        double d0 = (double) this.position.getX() + 0.5D;
        double d1 = (double) this.position.getY() + 0.5D;
        double d2 = (double) this.position.getZ() + 0.5D;
        this.world.a(null, d0, d1, d2, soundEffect, SoundCategory.BLOCKS, 0.5F, this.world.random.nextFloat() * 0.1F + 0.9F);
    }

}
