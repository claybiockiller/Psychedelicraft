/*
 *  Copyright (c) 2014, Lukas Tenbrink.
 *  * http://lukas.axxim.net
 */

package ivorius.psychedelicraft.events;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import ivorius.psychedelicraft.Psychedelicraft;
import ivorius.psychedelicraft.achievements.PSAchievementList;
import ivorius.psychedelicraft.blocks.PSBlocks;
import ivorius.psychedelicraft.client.audio.MovingSoundDrug;
import ivorius.psychedelicraft.config.PSConfig;
import ivorius.psychedelicraft.entities.drugs.DrugProperties;
import ivorius.psychedelicraft.fluids.FluidAlcohol;
import ivorius.psychedelicraft.fluids.FluidWithIconSymbolRegistering;
import ivorius.psychedelicraft.fluids.PSFluids;
import ivorius.psychedelicraft.items.PSItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.SoundHandler;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerSleepInBedEvent;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

/**
 * Created by lukas on 18.02.14.
 */
public class PSEventForgeHandler
{
    public static boolean containsAlcohol(FluidStack fluidStack, FluidAlcohol fluid, Boolean distilled, int minMatured)
    {
        return fluidStack != null
                && fluidStack.getFluid() == fluid
                && (distilled == null || (fluid.getDistillation(fluidStack) > 0) == distilled)
                && fluid.getMaturation(fluidStack) >= minMatured;
    }

    public void register()
    {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onServerChat(ServerChatEvent event)
    {
        if (PSConfig.distortOutgoingMessages)
        {
            Object[] args = event.component.getFormatArgs();

            if (args.length > 1)
            {
                DrugProperties drugProperties = DrugProperties.getDrugProperties(event.player);

                if (drugProperties != null)
                {
                    String modified = drugProperties.messageDistorter.distortOutgoingMessage(drugProperties, event.player, event.player.getRNG(), event.message);
                    args[1] = modified;
                }
            }
        }
    }

    @SubscribeEvent
    public void onClientChatReceived(ClientChatReceivedEvent event)
    {
        if (PSConfig.distortIncomingMessages && event.message instanceof ChatComponentText)
        {
            ChatComponentText text = (ChatComponentText) event.message;

            EntityLivingBase renderEntity = Minecraft.getMinecraft().renderViewEntity;
            DrugProperties drugProperties = DrugProperties.getDrugProperties(renderEntity);

            if (drugProperties != null)
            {
                String message = text.getUnformattedTextForChat();
                drugProperties.receiveChatMessage(renderEntity, message);
                String modified = drugProperties.messageDistorter.distortIncomingMessage(drugProperties, renderEntity, renderEntity.getRNG(), message);

                event.message = new ChatComponentText(modified);
            }
        }
    }

    @SubscribeEvent
    public void onPlayerSleep(PlayerSleepInBedEvent event)
    {
        DrugProperties drugProperties = DrugProperties.getDrugProperties(event.entityLiving);

        if (drugProperties != null)
        {
            EntityPlayer.EnumStatus status = drugProperties.getDrugSleepStatus();

            if (status != null)
                event.result = status;
        }
    }

    @SubscribeEvent
    public void onEntityJoinWorld(EntityJoinWorldEvent event)
    {
        DrugProperties drugProperties = DrugProperties.getDrugProperties(event.entity); // Initialize drug helper

        if (event.world.isRemote && drugProperties != null)
            initializeMovingSoundDrug(event.entity, drugProperties);
    }

    @SideOnly(Side.CLIENT)
    public void initializeMovingSoundDrug(Entity entity, DrugProperties drugProperties)
    {
        SoundHandler soundHandler = Minecraft.getMinecraft().getSoundHandler();
        for (String drugName : drugProperties.getAllDrugNames())
        {
            if (PSConfig.hasBGM(drugName))
                soundHandler.playSound(new MovingSoundDrug(new ResourceLocation(Psychedelicraft.MODID, "drug." + drugName.toLowerCase()), entity, drugProperties, drugName));
        }
    }

    @SubscribeEvent
    public void onEntityConstruction(EntityEvent.EntityConstructing event)
    {
        if (event.entity instanceof EntityPlayer)
            DrugProperties.initInEntity(event.entity);
    }

    @SubscribeEvent
    public void getBreakSpeed(PlayerEvent.BreakSpeed event)
    {
        DrugProperties drugProperties = DrugProperties.getDrugProperties(event.entity);

        if (drugProperties != null)
        {
            event.newSpeed = event.newSpeed * drugProperties.getDigSpeedModifier(event.entityLiving);
        }
    }

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Pre event)
    {
        if (event.type == RenderGameOverlayEvent.ElementType.PORTAL)
        {
            Minecraft mc = Minecraft.getMinecraft();
            EntityLivingBase renderEntity = mc.renderViewEntity;
            DrugProperties drugProperties = DrugProperties.getDrugProperties(renderEntity);

            if (drugProperties != null && drugProperties.renderer != null)
            {
                drugProperties.renderer.renderOverlaysAfterShaders(event.partialTicks, renderEntity, renderEntity.ticksExisted, event.resolution.getScaledWidth(), event.resolution.getScaledHeight(), drugProperties);
            }
        }
    }

    @SideOnly(Side.CLIENT)
    @SubscribeEvent
    public void onTextureStitchPre(TextureStitchEvent.Pre event)
    {
        IIconRegister iconRegister = event.map;
        for (Fluid fluid : FluidRegistry.getRegisteredFluids().values())
        {
            if (fluid instanceof FluidWithIconSymbolRegistering)
                ((FluidWithIconSymbolRegistering) fluid).registerIcons(iconRegister, event.map.getTextureType());
        }
    }

    @SubscribeEvent
    public void fluidDrinkEvent(FluidDrinkEvent event)
    {
        if (event.entityLiving instanceof EntityPlayer)
        {
            EntityPlayer player = (EntityPlayer) event.entityLiving;

            if (containsAlcohol(event.drunken, PSFluids.alcWheatHop, false, 0))
            {
                player.triggerAchievement(PSAchievementList.drankBeer);
            }
            if (containsAlcohol(event.drunken, PSFluids.alcWheatHop, false, 7))
            {
                player.triggerAchievement(PSAchievementList.drankMatureBeer);
            }

            if (containsAlcohol(event.drunken, PSFluids.alcRedGrapes, false, 0))
            {
                player.triggerAchievement(PSAchievementList.drankWine);
            }
            if (containsAlcohol(event.drunken, PSFluids.alcRedGrapes, true, 0))
            {
                player.triggerAchievement(PSAchievementList.drankWineBrandy);
            }

            if (containsAlcohol(event.drunken, PSFluids.alcSugarCane, false, 0))
            {
                player.triggerAchievement(PSAchievementList.drankBasi);
            }
            if (containsAlcohol(event.drunken, PSFluids.alcSugarCane, true, 0))
            {
                player.triggerAchievement(PSAchievementList.drankRum);
            }
        }
    }

    @SubscribeEvent
    public void fluidDrinkEvent(ItemDriedEvent event)
    {
        if (event.dried.isItemEqual(new ItemStack(PSItems.driedCannabisBuds)))
        {
            event.entityPlayer.triggerAchievement(PSAchievementList.driedCannabisBuds);
        }
        if (event.dried.isItemEqual(new ItemStack(PSItems.magicMushroomsRed)))
        {
            event.entityPlayer.triggerAchievement(PSAchievementList.driedRedShrooms);
        }
        if (event.dried.isItemEqual(new ItemStack(PSItems.magicMushroomsBrown)))
        {
            event.entityPlayer.triggerAchievement(PSAchievementList.driedBrownShrooms);
        }
    }
}
