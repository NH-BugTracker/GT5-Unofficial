package gregtech.cashgate;

import static gregtech.api.enums.Mods.GregTech;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.event.entity.living.LivingEvent;

import org.lwjgl.opengl.GL11;

import com.gtnewhorizon.gtnhlib.eventbus.EventBusSubscriber;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import gregtech.gtnhteams.CashTeamData;

@EventBusSubscriber
public class MoneyHudRenderer {

    private static final ResourceLocation MONEY_ICON = new ResourceLocation(
        GregTech.resourceDomain,
        "textures/gui/gregcoin.png");

    private static final int ICON_SIZE = 16;
    private static final int PADDING = 4;

    @SubscribeEvent
    public static void onJump(LivingEvent.LivingJumpEvent event) {
        if (event.entityLiving instanceof EntityPlayerMP player) {
            CashTeamData data = CashTeamData.getCashTeamFromPlayer(player);
            if (data != null) data.addCash(1);
        }
    }

    @SubscribeEvent
    public static void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.TEXT) return;

        Minecraft mc = Minecraft.getMinecraft();
        ScaledResolution res = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);

        CashTeamData team = CashTeamData.getCashTeamFromPlayer(mc.thePlayer);

        if (team != null) drawMoneyHud(mc, team.getCash());
    }

    private static void drawMoneyHud(Minecraft mc, long money) {
        int x = PADDING;
        int y = PADDING;

        drawIcon(mc, x, y);

        String display = "$" + String.format("%,d", money);
        int textX = x + ICON_SIZE + 3;
        int textY = y + (ICON_SIZE / 2) - (mc.fontRenderer.FONT_HEIGHT / 2);

        mc.fontRenderer.drawStringWithShadow(display, textX, textY, 0xFFD700);
    }

    private static void drawIcon(Minecraft mc, int x, int y) {
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_LIGHTING);
        OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        GL11.glColor4f(1f, 1f, 1f, 1f);

        mc.getTextureManager()
            .bindTexture(MONEY_ICON);

        Tessellator tess = Tessellator.instance;
        tess.startDrawingQuads();
        tess.addVertexWithUV(x, y + ICON_SIZE, 0, 0, 1);
        tess.addVertexWithUV(x + ICON_SIZE, y + ICON_SIZE, 0, 1, 1);
        tess.addVertexWithUV(x + ICON_SIZE, y, 0, 1, 0);
        tess.addVertexWithUV(x, y, 0, 0, 0);
        tess.draw();

        GL11.glDisable(GL11.GL_BLEND);
    }
}
