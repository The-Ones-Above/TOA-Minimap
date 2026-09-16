package com.theonesabove.minimap;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public final class MinimapEditScreen extends Screen {
    private final MinimapConfig config;
    private final MinimapHud hud;
    private boolean dragging;
    private double lastX, lastY;

    public MinimapEditScreen(MinimapConfig config, MinimapHud hud) {
        super(Component.literal("Edit TOA Minimap"));
        this.config = config; this.hud = hud; hud.setEditing(true);
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        hud.renderEditor(g);
        String hint = "Drag minimap to move  •  Scroll to resize  •  Esc to save";
        int w = font.width(hint), x=(width-w-12)/2, y=height-30;
        g.fill(x,y,x+w+12,y+18,0xB9000000); g.text(font,hint,x+6,y+5,0xFFD0D0D0,false);
    }
    @Override public boolean mouseClicked(MouseButtonEvent e, boolean dbl) {
        if (e.button()==GLFW.GLFW_MOUSE_BUTTON_LEFT && hud.bounds().contains(e.x(),e.y())) { dragging=true; lastX=e.x(); lastY=e.y(); return true; }
        return false;
    }
    @Override public boolean mouseDragged(MouseButtonEvent e,double dx,double dy) {
        if (!dragging || e.button()!=GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
        int mx=(int)Math.round(e.x()-lastX), my=(int)Math.round(e.y()-lastY); config.moveTransient(mx,my); lastX=e.x(); lastY=e.y(); return true;
    }
    @Override public boolean mouseReleased(MouseButtonEvent e) { if (e.button()==GLFW.GLFW_MOUSE_BUTTON_LEFT) { dragging=false; config.save(); return true; } return false; }
    @Override public boolean mouseScrolled(double mx,double my,double dx,double dy) { if (!hud.bounds().contains(mx,my)||dy==0) return false; config.resizeTransient(dy>0?4:-4); config.save(); return true; }
    @Override public void onClose() { dragging=false; config.save(); hud.setEditing(false); minecraft.gui.setScreen(null); }
    @Override public boolean isPauseScreen(){return false;} @Override public boolean isInGameUi(){return true;}
}
