package com.velo.client;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class VeloBlockPickerScreen extends Screen {
    private final Screen parent;
    private final List<Block> allBlocks = new ArrayList<>();
    private TextFieldWidget searchField;

    private float currentScroll = 0.0f;
    private float targetScroll = 0.0f;
    private float openAnim = 0.0f;
    private long lastFrameNanos = System.nanoTime();

    private int winX, winY, winW, winH;

    public VeloBlockPickerScreen(Screen parent) {
        super(Text.literal("Välj ESP-block"));
        this.parent = parent;

        for (Block block : Registries.BLOCK) {
            if (block == Blocks.AIR || block == Blocks.CAVE_AIR || block == Blocks.VOID_AIR) continue;
            allBlocks.add(block);
        }
        allBlocks.sort(Comparator.comparing(b -> b.getName().getString().toLowerCase(Locale.ROOT)));
    }

    @Override
    protected void init() {
        this.winW = Math.min(500, this.width - 28);
        this.winH = Math.min(316, this.height - 28);
        this.winX = (this.width - this.winW) / 2;
        this.winY = (this.height - this.winH) / 2;

        String prev = (searchField != null) ? searchField.getText() : "";
        searchField = new TextFieldWidget(this.textRenderer, winX + 18, winY + 42, winW - 36, 16, Text.literal("Sök block"));
        searchField.setDrawsBackground(false);
        searchField.setPlaceholder(Text.literal("Sök bland alla " + allBlocks.size() + " Minecraft-block..."));
        searchField.setText(prev);
        this.addDrawableChild(searchField);
        this.setInitialFocus(searchField);
        this.lastFrameNanos = System.nanoTime();
    }

    private List<Block> getFilteredBlocks() {
        String q = (searchField != null) ? searchField.getText().trim().toLowerCase(Locale.ROOT) : "";
        if (q.isEmpty()) return allBlocks;
        List<Block> out = new ArrayList<>();
        for (Block b : allBlocks) {
            String name = b.getName().getString().toLowerCase(Locale.ROOT);
            Identifier id = Registries.BLOCK.getId(b);
            if (name.contains(q) || id.getPath().contains(q)) {
                out.add(b);
            }
        }
        return out;
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        int bgAlpha = Math.clamp(Math.round(openAnim * 140.0f), 0, 140);
        context.fill(0, 0, this.width, this.height, (bgAlpha << 24) | 0x040812);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        long now = System.nanoTime();
        float dt = Math.min((now - lastFrameNanos) / 1_000_000_000.0f, 0.05f);
        lastFrameNanos = now;

        float smooth = 1.0f - (float) Math.exp(-15.0f * dt);
        openAnim += (1.0f - openAnim) * smooth;

        int accent = VeloClient.getAccentColor();
        int slideY = Math.round((1.0f - openAnim) * 14.0f);
        int wy = winY + slideY;

        VeloUiRenderer.drawSmoothPanel(context, winX, wy, winW, winH, 11, 0xEA0B101B, 0x8838BDF8, true);

        context.drawTextWithShadow(this.textRenderer,
                "§lBLOCK ESP §r§7•  " + VeloWorldFeatures.selectedEspBlocks.size() + " valda",
                winX + 16, wy + 14, accent);

        // RESET-knapp (stänger av / rensar alla valda block)
        int resetW = 72;
        int resetX = winX + winW - 148;
        int btnY = wy + 10;
        boolean resetHover = mouseX >= resetX && mouseX <= resetX + resetW && mouseY >= btnY && mouseY <= btnY + 20;
        int resetFill = resetHover ? 0xFFDC2626 : 0xCC3B1219;
        int resetBorder = resetHover ? 0xFFFF6B81 : 0xAAEF4444;
        VeloUiRenderer.drawSmoothPanel(context, resetX, btnY, resetW, 20, 6, resetFill, resetBorder, false);
        context.drawCenteredTextWithShadow(this.textRenderer, "Reset Alla", resetX + resetW / 2, btnY + 6, 0xFFFFFFFF);

        // KLAR-knapp
        int backW = 60;
        int backX = winX + winW - 70;
        boolean backHover = mouseX >= backX && mouseX <= backX + backW && mouseY >= btnY && mouseY <= btnY + 20;
        VeloUiRenderer.drawSmoothPanel(context, backX, btnY, backW, 20, 6,
                backHover ? VeloClient.getPrimaryBlue() : 0xFF16233B, accent, false);
        context.drawCenteredTextWithShadow(this.textRenderer, "Klar", backX + backW / 2, btnY + 6, 0xFFFFFFFF);

        // Sökruta
        VeloUiRenderer.drawSmoothPanel(context, winX + 12, wy + 36, winW - 24, 22, 7, 0xCC111929, 0x6638BDF8, false);

        List<Block> list = getFilteredBlocks();
        int listTop = wy + 66;
        int listBottom = wy + winH - 12;
        int rowH = 28;
        int cols = 2;
        int totalRows = (list.size() + cols - 1) / cols;
        float maxScroll = Math.max(0.0f, totalRows * rowH - (listBottom - listTop));
        targetScroll = MathHelper.clamp(targetScroll, 0.0f, maxScroll);
        currentScroll += (targetScroll - currentScroll) * (1.0f - (float) Math.exp(-16.0f * dt));

        int colW = (winW - 32) / cols;
        context.enableScissor(winX + 10, listTop, winX + winW - 10, listBottom);

        for (int i = 0; i < list.size(); i++) {
            int row = i / cols;
            int col = i % cols;
            int bx = winX + 12 + col * (colW + 8);
            int by = listTop + row * rowH - Math.round(currentScroll);
            if (by + rowH < listTop || by > listBottom) continue;

            Block block = list.get(i);
            boolean selected = VeloWorldFeatures.selectedEspBlocks.contains(block);
            boolean hovered = mouseX >= bx && mouseX <= bx + colW && mouseY >= by && mouseY <= by + rowH - 4
                    && mouseY >= listTop && mouseY <= listBottom;

            int fill = selected ? 0xDD13325E : (hovered ? 0xCC18253E : 0xB80E1626);
            int border = selected ? accent : (hovered ? 0x8838BDF8 : 0x44293C5E);
            VeloUiRenderer.drawSmoothPanel(context, bx, by, colW, rowH - 4, 7, fill, border, false);

            ItemStack stack = new ItemStack(block.asItem());
            if (!stack.isEmpty()) {
                context.drawItem(stack, bx + 5, by + 4);
            }
            String label = this.textRenderer.trimToWidth(block.getName().getString(), colW - 44);
            context.drawTextWithShadow(this.textRenderer, label, bx + 26, by + 8, selected ? 0xFFFFFFFF : 0xFFCBD5E1);

            if (selected) {
                VeloUiRenderer.drawSmoothRect(context, bx + colW - 12, by + 9, 6, 6, 3, accent);
            }
        }

        context.disableScissor();
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (click.button() == 0) {
            double mx = click.x(), my = click.y();
            int btnY = winY + 10;

            // Reset-knapp
            int resetX = winX + winW - 148;
            if (mx >= resetX && mx <= resetX + 72 && my >= btnY && my <= btnY + 20) {
                VeloWorldFeatures.selectedEspBlocks.clear();
                VeloWorldFeatures.blockEspEnabled = false;
                return true;
            }

            // Klar-knapp
            int backX = winX + winW - 70;
            if (mx >= backX && mx <= backX + 60 && my >= btnY && my <= btnY + 20) {
                if (this.client != null) this.client.setScreen(this.parent);
                return true;
            }

            int listTop = winY + 66;
            int listBottom = winY + winH - 12;
            if (my >= listTop && my <= listBottom) {
                List<Block> list = getFilteredBlocks();
                int rowH = 28, cols = 2, colW = (winW - 32) / cols;
                for (int i = 0; i < list.size(); i++) {
                    int row = i / cols, col = i % cols;
                    int bx = winX + 12 + col * (colW + 8);
                    int by = listTop + row * rowH - Math.round(currentScroll);
                    if (mx >= bx && mx <= bx + colW && my >= by && my <= by + rowH - 4) {
                        Block b = list.get(i);
                        if (!VeloWorldFeatures.selectedEspBlocks.remove(b)) {
                            VeloWorldFeatures.selectedEspBlocks.add(b);
                        }
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        targetScroll -= (float) (verticalAmount * 36.0);
        return true;
    }

    @Override
    public void close() {
        if (this.client != null) this.client.setScreen(this.parent);
    }
}
