package umpaz.brewinandchewin.common.block.entity.inventory;

import com.google.common.collect.Lists;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ClientboundPlaceGhostRecipePacket;
import net.minecraft.recipebook.PlaceRecipe;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.StackedContents;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraftforge.registries.RegistryObject;
import umpaz.brewinandchewin.common.crafting.KegRecipe;

import javax.annotation.Nullable;
import java.util.Iterator;
import java.util.List;

public class KegServerPlaceRecipe<C extends Container> implements PlaceRecipe<Integer> {
    protected final StackedContents stackedContents = new StackedContents();
    protected Inventory inventory;
    protected RecipeBookMenu<C> menu;

    public KegServerPlaceRecipe(RecipeBookMenu<C> keg) {
        this.menu = keg;
    }

    public void recipeClicked(ServerPlayer player, @Nullable Recipe<C> recipe, boolean p_135437_) {
        if (recipe != null && player.getRecipeBook().contains(recipe)) {
            this.inventory = player.getInventory();
            if (this.testClearGrid() || player.isCreative()) {
                this.stackedContents.clear();
                player.getInventory().fillStackedContents(this.stackedContents);
                this.menu.fillCraftSlotsStackedContents(this.stackedContents);
                if (recipe instanceof KegRecipe fermentableRecipe) {
                    if (this.stackedContents.canCraft(recipe, (IntList) null) && this.stackedContents.contents.containsKey(Item.getId(fermentableRecipe.getFluidItem().getItem()))) {
                        this.handleRecipeClicked(recipe, p_135437_);
                        this.moveItemToGrid(menu.getSlot(4), fermentableRecipe.getFluidItem());
                    } else {
                        this.clearGrid(true);
                        player.connection.send(new ClientboundPlaceGhostRecipePacket(player.containerMenu.containerId, recipe));
                    }

                    player.getInventory().setChanged();
                }
            }
        }
    }

    protected void clearGrid(boolean p_179845_) {
        for(int i = 0; i < this.menu.getSize(); ++i) {
            if (this.menu.shouldMoveToInventory(i)) {
                ItemStack itemstack = this.menu.getSlot(i).getItem().copy();
                this.inventory.placeItemBackInInventory(itemstack, false);
                this.menu.getSlot(i).set(itemstack);
            }
        }

        this.menu.clearCraftingContent();
    }

    protected void handleRecipeClicked(Recipe<C> recipe, boolean p_135442_) {
        boolean flag = this.menu.recipeMatches(recipe);
        int i = this.stackedContents.getBiggestCraftableStack(recipe, (IntList)null);
        if (flag) {
            for(int j = 0; j < this.menu.getGridHeight() * this.menu.getGridWidth() + 1; ++j) {
                if (j != this.menu.getResultSlotIndex()) {
                    ItemStack itemstack = this.menu.getSlot(j).getItem();
                    if (!itemstack.isEmpty() && Math.min(i, itemstack.getMaxStackSize()) < itemstack.getCount() + 1) {
                        return;
                    }
                }
            }
        }

        int j1 = this.getStackSize(p_135442_, i, flag);
        IntList intlist = new IntArrayList();
        if (this.stackedContents.canCraft(recipe, intlist, j1)) {
            int k = j1;

            for(int l : intlist) {
                int i1 = StackedContents.fromStackingIndex(l).getMaxStackSize();
                if (i1 < k) {
                    k = i1;
                }
            }

            if (this.stackedContents.canCraft(recipe, intlist, k)) {
                intlist.remove(intlist.size() - 1);
                this.clearGrid(false);
                this.placeRecipe(this.menu.getGridWidth(), this.menu.getGridHeight(), this.menu.getResultSlotIndex(), recipe, intlist.iterator(), k);
            }
        }

    }

    public void addItemToSlot(Iterator<Integer> p_135444_, int p_135445_, int p_135446_, int p_135447_, int p_135448_) {
        Slot slot = this.menu.getSlot(p_135445_);
        ItemStack itemstack = StackedContents.fromStackingIndex(p_135444_.next());
        if (!itemstack.isEmpty()) {
            for(int i = 0; i < p_135446_; ++i) {
                this.moveItemToGrid(slot, itemstack);
            }
        }

    }

    protected int getStackSize(boolean p_135450_, int p_135451_, boolean p_135452_) {
        int i = 1;
        if (p_135450_) {
            i = p_135451_;
        } else if (p_135452_) {
            i = 64;

            for(int j = 0; j < this.menu.getGridWidth() * this.menu.getGridHeight() + 1; ++j) {
                if (j != this.menu.getResultSlotIndex()) {
                    ItemStack itemstack = this.menu.getSlot(j).getItem();
                    if (!itemstack.isEmpty() && i > itemstack.getCount()) {
                        i = itemstack.getCount();
                    }
                }
            }

            if (i < 64) {
                ++i;
            }
        }

        return i;
    }

    protected void moveItemToGrid(Slot fluidSlot, ItemStack fluidItem) {
        int i = this.inventory.findSlotMatchingUnusedItem(fluidItem);
        if (i != -1) {
            ItemStack itemstack = this.inventory.getItem(i).copy();
            if (!itemstack.isEmpty()) {
                if (fluidSlot.getItem().getCount() < fluidItem.getMaxStackSize()) {
                    if (itemstack.getCount() > 1) {
                        this.inventory.removeItem(i, 1);
                    } else {
                        this.inventory.removeItemNoUpdate(i);
                    }
                }

                itemstack.setCount(1);
                if (fluidSlot.getItem().isEmpty()) {
                    fluidSlot.set(itemstack);
                } else if (fluidSlot.getItem().getCount() < fluidItem.getMaxStackSize()) {
                    fluidSlot.getItem().grow(1);
                }

            }
        }
    }

    private boolean testClearGrid() {
        List<ItemStack> list = Lists.newArrayList();
        int i = this.getAmountOfFreeSlotsInInventory();

        for(int j = 0; j < this.menu.getGridWidth() * this.menu.getGridHeight() + 1; ++j) {
            if (j != this.menu.getResultSlotIndex()) {
                ItemStack itemstack = this.menu.getSlot(j).getItem().copy();
                if (!itemstack.isEmpty()) {
                    int k = this.inventory.getSlotWithRemainingSpace(itemstack);
                    if (k == -1 && list.size() <= i) {
                        for(ItemStack itemstack1 : list) {
                            if (ItemStack.isSameItem(itemstack1, itemstack) && itemstack1.getCount() != itemstack1.getMaxStackSize() && itemstack1.getCount() + itemstack.getCount() <= itemstack1.getMaxStackSize()) {
                                itemstack1.grow(itemstack.getCount());
                                itemstack.setCount(0);
                                break;
                            }
                        }

                        if (!itemstack.isEmpty()) {
                            if (list.size() >= i) {
                                return false;
                            }

                            list.add(itemstack);
                        }
                    } else if (k == -1) {
                        return false;
                    }
                }
            }
        }

        return true;
    }

    private int getAmountOfFreeSlotsInInventory() {
        int i = 0;

        for(ItemStack itemstack : this.inventory.items) {
            if (itemstack.isEmpty()) {
                ++i;
            }
        }

        return i;
    }
}