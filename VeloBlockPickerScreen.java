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
    private int scrollOffset = 0;

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
        this.winW = Math.min(460, this.width - 30);
        this.winH = Math.min(300, this.height - 30);
        this.winX = (this.width - this.winW) / 2;
        this.winY = (this.height - this.winH) / 2;

        String prev = (searchField != null) ? searchField.getText() : "";
        searchField = new TextFieldWidget(this.textRenderer, winX + 16, winY + 38, winW - 32, 18, Text.literal("Sök block"));
        searchField.setDrawsBackground(false);
        searchField.setPlaceholder(Text.literal("Sök bland alla " + allBlocks.size() + " Minecraft-block..."));
        searchField.setText(prev);
        this.addDrawableChild(searchField);
        this.setInitialFocus(searchField);
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
        context.fill(0, 0, this.width, this.height, 0x99040914);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        int accent = VeloClient.getAccentColor();
        VeloUiRenderer.drawSmoothPanel(context, winX, winY, winW, winH, 0xF20A1224, 0xFF1E3A6E);

        context.drawTextWithShadow(this.textRenderer,
                "§lBLOCK ESP §r§7(" + VeloWorldFeatures.selectedEspBlocks.size() + " valda)",
                winX + 16, winY + 14, accent);

        int backX = winX + winW - 76;
        int backY = winY + 10;
        boolean backHover = mouseX >= backX && mouseX <= backX + 64 && mouseY >= backY && mouseY <= backY + 20;
        VeloUiRenderer.drawSmoothPanel(context, backX, backY, 64, 20, backHover ? VeloClient.getPrimaryBlue() : 0xFF13223D, accent);
        context.drawCenteredTextWithShadow(this.textRenderer, "Klar", backX + 32, backY + 6, 0xFFFFFFFF);

        VeloUiRenderer.drawSmoothPanel(context, winX + 12, winY + 34, winW - 24, 22, 0xFF0E1A30, 0xFF233B66);

        List<Block> list = getFilteredBlocks();
        int listTop = winY + 64;
        int listBottom = winY + winH - 12;
        int rowH = 26;
        int cols = 2;
        int totalRows = (list.size() + cols - 1) / cols;
        int maxScroll = Math.max(0, totalRows * rowH - (listBottom - listTop));
        scrollOffset = MathHelper.clamp(scrollOffset, 0, maxScroll);

        int colW = (winW - 32) / cols;
        context.enableScissor(winX + 10, listTop, winX + winW - 10, listBottom);

        for (int i = 0; i < list.size(); i++) {
            int row = i / cols;
            int col = i % cols;
            int bx = winX + 12 + col * (colW + 8);
            int by = listTop + row * rowH - scrollOffset;
            if (by + rowH < listTop || by > listBottom) continue;

            Block block = list.get(i);
            boolean selected = VeloWorldFeatures.selectedEspBlocks.contains(block);
            boolean hovered = mouseX >= bx && mouseX <= bx + colW && mouseY >= by && mouseY <= by + rowH - 3
                    && mouseY >= listTop && mouseY <= listBottom;

            int fill = selected ? 0xFF153463 : (hovered ? 0xFF142442 : 0xCC0D172B);
            int border = selected ? accent : 0xFF1D2F52;
            VeloUiRenderer.drawSmoothPanel(context, bx, by, colW, rowH - 3, fill, border);

            ItemStack stack = new ItemStack(block.asItem());
            if (!stack.isEmpty()) {
                context.drawItem(stack, bx + 4, by + 3);
            }
            String label = this.textRenderer.trimToWidth(block.getName().getString(), colW - 34);
            context.drawTextWithShadow(this.textRenderer, label, bx + 24, by + 7, selected ? 0xFFFFFFFF : 0xFFCBD5E1);
        }

        context.disableScissor();
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (click.button() == 0) {
            double mx = click.x(), my = click.y();
            int backX = winX + winW - 76, backY = winY + 10;
            if (mx >= backX && mx <= backX + 64 && my >= backY && my <= backY + 20) {
                if (this.client != null) this.client.setScreen(this.parent);
                return true;
            }

            int listTop = winY + 64;
            int listBottom = winY + winH - 12;
            if (my >= listTop && my <= listBottom) {
                List<Block> list = getFilteredBlocks();
                int rowH = 26, cols = 2, colW = (winW - 32) / cols;
                for (int i = 0; i < list.size(); i++) {
                    int row = i / cols, col = i % cols;
                    int bx = winX + 12 + col * (colW + 8);
                    int by = listTop + row * rowH - scrollOffset;
                    if (mx >= bx && mx <= bx + colW && my >= by && my <= by + rowH - 3) {
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
        scrollOffset -= (int) (verticalAmount * 26);
        return true;
    }

    @Override
    public void close() {
        if (this.client != null) this.client.setScreen(this.parent);
    }
}
