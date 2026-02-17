package gregtech.common.gui.modularui.item;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import net.minecraft.client.Minecraft;

import org.lwjgl.input.Keyboard;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.drawable.Rectangle;
import com.cleanroommc.modularui.factory.GuiData;
import com.cleanroommc.modularui.network.NetworkUtils;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.utils.Color;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.PagedWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.layout.Column;
import com.cleanroommc.modularui.widgets.layout.Grid;
import com.cleanroommc.modularui.widgets.layout.Row;
import com.github.bsideup.jabel.Desugar;

import gregtech.api.modularui2.GTWidgetThemes;

public class ItemPhoneGui {

    public ItemPhoneGui(GuiData guiData, PanelSyncManager guiSyncManager) {
        registerBuiltInApps();
    }

    // ---------------- Config ----------------
    private static final class C {

        static final float PANEL_WIDTH_REL = 0.6f;
        static final float PANEL_HEIGHT_REL = 0.8f;
        static final int TEXT_COLOR = Color.rgb(255, 255, 255);
        static final int BG_COLOR_PRIMARY = Color.rgb(50, 50, 50);
        static final int BG_COLOR_SECONDARY = Color.rgb(70, 70, 70);
        static final int BUTTON_SIZE = 40;
        static final int GRID_CELL_SIZE = 10;
        static final int HOME_GRID_COLS = 9;
        static final int HOME_GRID_ROWS = 9;
    }

    // ---------------- Utilities ----------------
    @Desugar
    private record Cell(int x, int y) {

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Cell)) return false;
            Cell c = (Cell) o;
            return c.x == x && c.y == y;
        }

        @Override
        public int hashCode() {
            return (x * 31) ^ y;
        }

        @Override
        public String toString() {
            return "Cell(" + x + "," + y + ")";
        }
    }

    // ---------------- App management ----------------
    private final List<PhoneApp> apps = new ArrayList<>();
    private final Map<Cell, AppIcon> appGrid = new HashMap<>();
    private final Map<Integer, Integer> globalKeyBindings = new HashMap<>();
    private boolean builtInsRegistered = false;
    private final Rectangle bgPrimaryRect = new Rectangle().setColor(C.BG_COLOR_PRIMARY);
    private final Rectangle bgSecondaryRect = new Rectangle().setColor(C.BG_COLOR_SECONDARY);

    private static class AppIcon {

        final int gridX, gridY;
        final PhoneApp app;
        final String displayName;
        final boolean textured;
        final String texturePath;
        final int color;

        AppIcon(int gx, int gy, PhoneApp app, String displayName, int color) {
            this.gridX = gx;
            this.gridY = gy;
            this.app = app;
            this.displayName = displayName;
            this.textured = false;
            this.texturePath = null;
            this.color = color;
        }

        AppIcon(int gx, int gy, PhoneApp app, String displayName, String texturePath) {
            this.gridX = gx;
            this.gridY = gy;
            this.app = app;
            this.displayName = displayName;
            this.textured = true;
            this.texturePath = texturePath;
            this.color = 0;
        }
    }

    private void registerBuiltInApps() {
        if (builtInsRegistered) return;
        builtInsRegistered = true;

        PhoneApp snake = new SnakeApp();
        PhoneApp tetris = new TetrisApp();
        registerApp(snake);
        registerApp(tetris);

        addAppAt(tetris, 0, 0, "Tetris", TetrisGame.PIECE_COLORS[0]);
        addAppAt(snake, 1, 0, "Snake", SnakeGame.BODY_COLOR);
    }

    public void registerApp(PhoneApp app) {
        if (!apps.contains(app)) apps.add(app);
    }

    public void addAppAt(PhoneApp app, int gridX, int gridY, String displayName, int color) {
        if (!isValidHomeCell(gridX, gridY)) return;
        Cell key = new Cell(gridX, gridY);
        appGrid.put(key, new AppIcon(gridX, gridY, app, displayName, color));
        registerApp(app);
    }

    public void addAppAtTexture(PhoneApp app, int gridX, int gridY, String displayName, String texturePath) {
        if (!isValidHomeCell(gridX, gridY)) return;
        Cell key = new Cell(gridX, gridY);
        appGrid.put(key, new AppIcon(gridX, gridY, app, displayName, texturePath));
        registerApp(app);
    }

    public void bindKeyToApp(int keyCode, int pageIndex) {
        globalKeyBindings.put(keyCode, pageIndex);
    }

    private boolean isValidHomeCell(int x, int y) {
        return x >= 0 && x < C.HOME_GRID_COLS && y >= 0 && y < C.HOME_GRID_ROWS;
    }

    // ---------------- Panel build ----------------
    public ModularPanel build() {
        registerBuiltInApps();

        ModularPanel panel = ModularPanel.defaultPanel("phone_gui");
        panel.flex()
            .sizeRel(C.PANEL_WIDTH_REL, C.PANEL_HEIGHT_REL)
            .align(Alignment.Center);
        panel.background(bgPrimaryRect);

        PagedWidget.Controller controller = new PagedWidget.Controller();
        PagedWidget pages = new PagedWidget<>().sizeRel(1f, 1f)
            .controller(controller);

        pages.addPage(buildHomePage(controller));

        // each app: create model + renderer and pass them to app to build UI
        for (int i = 0; i < apps.size(); i++) {
            PhoneApp app = apps.get(i);
            GameBase model = app.createGame();
            IWidget page = app.buildUI(model, controller, i + 1);
            pages.addPage(page);
        }

        panel.child(pages);
        return panel.widgetTheme(GTWidgetThemes.BACKGROUND_TERMINAL);
    }

    private IWidget buildHomePage(PagedWidget.Controller controller) {
        Grid grid = new Grid().sizeRel(1f, 1f);

        for (int y = 0; y < C.HOME_GRID_ROWS; y++) {
            grid.nextRow();
            for (int x = 0; x < C.HOME_GRID_COLS; x++) {
                Cell key = new Cell(x, y);
                AppIcon icon = appGrid.get(key);
                Column cell = new Column();
                cell.size(C.BUTTON_SIZE + 16, C.BUTTON_SIZE + 20)
                    .margin(4);

                if (icon == null) {
                    cell.child(
                        new Widget<>().size(C.BUTTON_SIZE, C.BUTTON_SIZE)
                            .background(bgPrimaryRect));
                    cell.child(
                        new TextWidget<>(IKey.str("")).alignment(Alignment.Center)
                            .color(C.TEXT_COLOR)
                            .marginTop(3));
                    grid.child(cell);
                    continue;
                }

                final int pageIndex = apps.indexOf(icon.app) + 1;
                ButtonWidget<?> btn = new ButtonWidget<>().size(C.BUTTON_SIZE, C.BUTTON_SIZE)
                    .onMousePressed(b -> {
                        controller.setPage(pageIndex);
                        return true;
                    });

                Rectangle iconBg = icon.textured ? bgSecondaryRect : new Rectangle().setColor(icon.color);
                btn.background(iconBg);
                try {
                    btn.hoverBackground(iconBg);
                } catch (Throwable ignored) {}

                cell.child(btn);
                cell.child(
                    new TextWidget<>(IKey.str(icon.displayName)).alignment(Alignment.Center)
                        .color(C.TEXT_COLOR)
                        .marginTop(3));
                grid.child(cell);
            }
        }

        return new Column().sizeRel(1f, 1f)
            .child(grid);
    }

    // ---------------- PhoneApp interface (MVC) ----------------
    public interface PhoneApp {

        String getName();

        GameBase createGame(); // model

        IWidget buildUI(GameBase model, PagedWidget.Controller controller, int pageIndex); // view + controller binding
    }

    // ---------------- Input handling (debounce & autorepeat) ----------------
    private static class InputHandler {

        private boolean upPressed, downPressed, leftPressed, rightPressed, actionPressed;
        private long leftLastRepeat, rightLastRepeat;
        private static final long LR_INITIAL_DELAY = 200;
        private static final long LR_REPEAT_RATE = 70;

        public boolean consumePress(boolean keyState, KeyAction onPress) {
            if (keyState && !wasPressed(onPress)) {
                setPressed(onPress, true);
                return true;
            } else if (!keyState) {
                setPressed(onPress, false);
            }
            return false;
        }

        // convenience for specific keys
        public boolean tryLeft(boolean keyLeft, Runnable doLeft) {
            long now = System.currentTimeMillis();
            if (keyLeft) {
                if (!leftPressed) {
                    leftPressed = true;
                    leftLastRepeat = now;
                    doLeft.run();
                } else {
                    long elapsed = now - leftLastRepeat;
                    if (elapsed >= LR_INITIAL_DELAY && elapsed >= LR_REPEAT_RATE) {
                        doLeft.run();
                        leftLastRepeat = now;
                    }
                }
            } else leftPressed = false;
            return keyLeft;
        }

        public boolean tryRight(boolean keyRight, Runnable doRight) {
            long now = System.currentTimeMillis();
            if (keyRight) {
                if (!rightPressed) {
                    rightPressed = true;
                    rightLastRepeat = now;
                    doRight.run();
                } else {
                    long elapsed = now - rightLastRepeat;
                    if (elapsed >= LR_INITIAL_DELAY && elapsed >= LR_REPEAT_RATE) {
                        doRight.run();
                        rightLastRepeat = now;
                    }
                }
            } else rightPressed = false;
            return keyRight;
        }

        public boolean wasPressed(KeyAction a) {
            return switch (a) {
                case UP -> upPressed;
                case DOWN -> downPressed;
                case ACTION -> actionPressed;
            };
        }

        public void setPressed(KeyAction a, boolean v) {
            switch (a) {
                case UP:
                    upPressed = v;
                    break;
                case DOWN:
                    downPressed = v;
                    break;
                case ACTION:
                    actionPressed = v;
                    break;
            }
        }

        enum KeyAction {
            UP,
            DOWN,
            ACTION
        }
    }

    // ---------------- GameController (input + tick + render) ----------------
    private static class GameController extends Column {

        private final GameBase model;
        private final List<GameRenderer> renderers;
        private final PagedWidget.Controller controller;
        private final ItemPhoneGui gui;
        private final InputHandler input = new InputHandler();
        private long lastUpdate = 0;

        GameController(GameBase model, List<GameRenderer> renderers, PagedWidget.Controller controller,
            ItemPhoneGui gui) {
            this.model = model;
            this.renderers = renderers;
            this.controller = controller;
            this.gui = gui;
        }

        @Override
        public void onUpdate() {
            super.onUpdate();
            if (!NetworkUtils.isClient()) return;
            long now = System.currentTimeMillis();

            var gs = Minecraft.getMinecraft().gameSettings;
            boolean keyUp = Keyboard.isKeyDown(gs.keyBindForward.getKeyCode());
            boolean keyLeft = Keyboard.isKeyDown(gs.keyBindLeft.getKeyCode());
            boolean keyDown = Keyboard.isKeyDown(gs.keyBindBack.getKeyCode());
            boolean keySneak = Keyboard.isKeyDown(gs.keyBindSneak.getKeyCode());
            boolean keyRight = Keyboard.isKeyDown(gs.keyBindRight.getKeyCode());
            boolean keyAction1 = Keyboard.isKeyDown(gs.keyBindJump.getKeyCode());
            boolean keyActionOther = Keyboard.isKeyDown(gs.keyBindUseItem.getKeyCode())
                || Keyboard.isKeyDown(gs.keyBindAttack.getKeyCode());

            // global key bindings
            for (Map.Entry<Integer, Integer> e : gui.globalKeyBindings.entrySet()) {
                if (Keyboard.isKeyDown(e.getKey())) controller.setPage(e.getValue());
            }

            model.setDropMultiplier(keySneak ? 4 : 1);

            // UP
            if (input.consumePress(keyUp, InputHandler.KeyAction.UP)) {
                if (model instanceof TetrisGame) model.rotate();
                else model.up();
            }

            // LEFT / RIGHT (autorepeat)
            input.tryLeft(keyLeft, model::left);
            input.tryRight(keyRight, model::right);

            // DOWN single press
            if (input.consumePress(keyDown, InputHandler.KeyAction.DOWN)) {
                model.down();
            }

            // ACTIONS
            if ((keyAction1 || keyActionOther) && !input.wasPressed(InputHandler.KeyAction.ACTION)) {
                if (model instanceof TetrisGame && keyAction1) {
                    model.hardDrop();
                } else if (model instanceof TetrisGame && keyActionOther) {
                    model.rotate();
                } else {
                    model.action();
                }
                input.setPressed(InputHandler.KeyAction.ACTION, true);
            } else if (!keyAction1 && !keyActionOther) {
                input.setPressed(InputHandler.KeyAction.ACTION, false);
            }

            // Tick
            if (now - lastUpdate >= model.getUpdateInterval()) {
                model.update();
                lastUpdate = now;
            }

            // Render ALL renderers
            for (GameRenderer renderer : renderers) {
                renderer.renderFromModel(model);
            }
        }
    }

    // ---------------- GameModel base (no UI) ----------------
    public static abstract class GameBase {

        protected int dropMultiplier = 1;

        public abstract void update();

        public abstract void up();

        public abstract void down();

        public abstract void left();

        public abstract void right();

        public abstract void action();

        public void rotate() {
            action();
        }

        public void hardDrop() {}

        public void setDropMultiplier(int m) {
            dropMultiplier = Math.max(1, m);
        }

        public abstract int getUpdateInterval();

        public abstract int getWidth();

        public abstract int getHeight();

        public abstract int getCellColor(int x, int y);
    }

    // ---------------- Renderer: обновляет только изменившиеся ячейки ----------------
    private interface GameRenderer {

        void renderFromModel(GameBase model);
    }

    private static class GridRenderer implements GameRenderer {

        private final Widget[][] cells;
        private final int w, h;
        private final Rectangle bgRect;
        private final Map<Integer, Rectangle> rectCache = new HashMap<>();
        private final int[][] prevColors;

        GridRenderer(Widget[][] cells, int bgColor) {
            this.cells = cells;
            this.w = cells.length;
            this.h = cells[0].length;
            this.bgRect = new Rectangle().setColor(bgColor);
            this.prevColors = new int[w][h];
            for (int x = 0; x < w; x++) for (int y = 0; y < h; y++) prevColors[x][y] = bgColor;
            rectCache.put(bgColor, bgRect);
        }

        private Rectangle getRect(int color) {
            return rectCache.computeIfAbsent(color, c -> new Rectangle().setColor(c));
        }

        @Override
        public void renderFromModel(GameBase model) {
            int mw = model.getWidth(), mh = model.getHeight();
            int maxX = Math.min(w, mw);
            int maxY = Math.min(h, mh);
            for (int x = 0; x < maxX; x++) {
                for (int y = 0; y < maxY; y++) {
                    int desired = model.getCellColor(x, y);
                    if (prevColors[x][y] != desired) {
                        Widget cell = cells[x][y];
                        Rectangle rect = getRect(desired);
                        cell.background(rect);
                        try {
                            cell.hoverBackground(rect);
                        } catch (Throwable ignored) {}
                        prevColors[x][y] = desired;
                    }
                }
            }
            for (int x = maxX; x < w; x++) for (int y = 0; y < h; y++) {
                if (prevColors[x][y] != bgRect.getColor()) {
                    cells[x][y].background(bgRect);
                    prevColors[x][y] = bgRect.getColor();
                }
            }
            for (int x = 0; x < w; x++) for (int y = maxY; y < h; y++) {
                if (prevColors[x][y] != bgRect.getColor()) {
                    cells[x][y].background(bgRect);
                    prevColors[x][y] = bgRect.getColor();
                }
            }
        }
    }

    public static class SnakeGame extends GameBase {

        static final int GRID_WIDTH = 20;
        static final int GRID_HEIGHT = 20;
        static final int INITIAL_LENGTH = 3;
        static final int UPDATE_INTERVAL_MS = 200;
        static final int APPLE_COLOR = Color.rgb(255, 0, 0);
        static final int BODY_COLOR = Color.rgb(0, 255, 0);
        static final int HEAD_COLOR = Color.rgb(0, 200, 0);

        private final List<Cell> snake = new ArrayList<>();
        private final Set<Cell> snakeSet = new HashSet<>();
        private Cell apple = new Cell(0, 0);
        private int dx = 1, dy = 0;
        private boolean growing = false;
        private final Random rand = new Random();

        public SnakeGame() {
            initSnake();
            spawnApple();
        }

        private void initSnake() {
            snake.clear();
            snakeSet.clear();
            int cx = GRID_WIDTH / 2;
            int cy = GRID_HEIGHT / 2;
            for (int i = 0; i < INITIAL_LENGTH; i++) {
                int x = cx + i;
                Cell c = new Cell(x, cy);
                snake.add(c);
                snakeSet.add(c);
            }
            dx = -1;
            dy = 0;
        }

        private void spawnApple() {
            int ax, ay;
            do {
                ax = rand.nextInt(GRID_WIDTH);
                ay = rand.nextInt(GRID_HEIGHT);
            } while (snakeSet.contains(new Cell(ax, ay)));
            apple = new Cell(ax, ay);
        }

        @Override
        public void update() {
            Cell head = snake.get(0);
            int newX = head.x + dx;
            int newY = head.y + dy;
            Cell newHead = new Cell(newX, newY);
            if (newX < 0 || newX >= GRID_WIDTH || newY < 0 || newY >= GRID_HEIGHT || snakeSet.contains(newHead)) {
                // game over -> reset
                initSnake();
                spawnApple();
                return;
            }
            snake.add(0, newHead);
            snakeSet.add(newHead);
            if (newX == apple.x && newY == apple.y) {
                growing = true;
                spawnApple();
            }
            if (!growing) {
                Cell tail = snake.remove(snake.size() - 1);
                snakeSet.remove(tail);
            } else {
                growing = false;
            }
        }

        @Override
        public void up() {
            if (dy == 0) {
                dx = 0;
                dy = -1;
            }
        }

        @Override
        public void down() {
            if (dy == 0) {
                dx = 0;
                dy = 1;
            }
        }

        @Override
        public void left() {
            if (dx == 0) {
                dx = -1;
                dy = 0;
            }
        }

        @Override
        public void right() {
            if (dx == 0) {
                dx = 1;
                dy = 0;
            }
        }

        @Override
        public void action() {}

        @Override
        public int getUpdateInterval() {
            return Math.max(50, UPDATE_INTERVAL_MS / Math.max(1, dropMultiplier));
        }

        @Override
        public int getWidth() {
            return GRID_WIDTH;
        }

        @Override
        public int getHeight() {
            return GRID_HEIGHT;
        }

        @Override
        public int getCellColor(int x, int y) {
            // apple
            if (apple.x == x && apple.y == y) return APPLE_COLOR;
            for (int i = 0; i < snake.size(); i++) {
                Cell p = snake.get(i);
                if (p.x == x && p.y == y) return (i == 0) ? HEAD_COLOR : BODY_COLOR;
            }
            return C.BG_COLOR_PRIMARY;
        }
    }

    // ---------------- Tetris (model) ----------------
    public static class TetrisGame extends GameBase {

        static final int GRID_WIDTH = 10;
        static final int GRID_HEIGHT = 20;
        static final int UPDATE_INTERVAL_MS = 500;
        static final int PLACED_COLOR = Color.rgb(128, 128, 128);
        static final int GHOST_COLOR = Color.argb(255, 255, 255, 10);
        static final int[] PIECE_COLORS = { Color.rgb(0, 240, 240), Color.rgb(0, 0, 240), Color.rgb(240, 160, 0),
            Color.rgb(240, 240, 0), Color.rgb(0, 240, 0), Color.rgb(160, 0, 240), Color.rgb(240, 0, 0) };

        private final boolean[][] board = new boolean[GRID_WIDTH][GRID_HEIGHT];
        private final int[][][] pieces = { { { 0, 0 }, { 1, 0 }, { 2, 0 }, { 3, 0 } }, // I
            { { 0, 0 }, { 0, 1 }, { 1, 1 }, { 2, 1 } }, // J
            { { 2, 0 }, { 0, 1 }, { 1, 1 }, { 2, 1 } }, // L
            { { 0, 0 }, { 1, 0 }, { 0, 1 }, { 1, 1 } }, // O
            { { 1, 0 }, { 2, 0 }, { 0, 1 }, { 1, 1 } }, // S
            { { 1, 0 }, { 0, 1 }, { 1, 1 }, { 2, 1 } }, // T
            { { 0, 0 }, { 1, 0 }, { 1, 1 }, { 2, 1 } } // Z
        };

        private final int[][][][] pieceRotations;
        private final int[] currentPiecePos = new int[2];
        private int[][] currentPiece;
        private int currentRotation = 0;
        private int currentType;
        private int nextType;
        private final Random rand = new Random();

        public TetrisGame() {
            pieceRotations = precomputeRotations(pieces);
            this.nextType = rand.nextInt(pieces.length);
            spawnPiece();
        }

        private int[][][][] precomputeRotations(int[][][] srcPieces) {
            int types = srcPieces.length;
            int[][][][] out = new int[types][4][][];
            for (int t = 0; t < types; t++) {
                for (int r = 0; r < 4; r++) {
                    int[][] clone = deepClone(srcPieces[t]);
                    clone = rotateCoords(clone, r);
                    normalizePiece(clone);
                    out[t][r] = clone;
                }
            }
            return out;
        }

        private int[][] deepClone(int[][] src) {
            int[][] out = new int[src.length][2];
            for (int i = 0; i < src.length; i++) out[i] = Arrays.copyOf(src[i], 2);
            return out;
        }

        private int[][] rotateCoords(int[][] piece, int times) {
            int[][] res = deepClone(piece);
            for (int t = 0; t < times; t++) {
                for (int[] coord : res) {
                    int x = coord[0];
                    coord[0] = -coord[1];
                    coord[1] = x;
                }
                normalizePiece(res);
            }
            return res;
        }

        private void normalizePiece(int[][] piece) {
            int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
            for (int[] c : piece) {
                minX = Math.min(minX, c[0]);
                minY = Math.min(minY, c[1]);
            }
            for (int[] c : piece) {
                c[0] -= minX;
                c[1] -= minY;
            }
        }

        private void spawnPiece() {
            currentType = nextType;
            nextType = rand.nextInt(pieces.length);
            currentPiece = deepClone(pieces[currentType]);
            normalizePiece(currentPiece);
            currentPiecePos[0] = GRID_WIDTH / 2 - 2;
            currentPiecePos[1] = 0;
            currentRotation = 0;
        }

        @Override
        public void update() {
            if (canMove(0, 1)) currentPiecePos[1]++;
            else {
                placePiece();
                clearLines();
                spawnPiece();
            }
        }

        private boolean canMove(int dx, int dy) {
            for (int[] cell : currentPiece) {
                int nx = currentPiecePos[0] + cell[0] + dx;
                int ny = currentPiecePos[1] + cell[1] + dy;
                if (nx < 0 || nx >= GRID_WIDTH || ny < 0 || ny >= GRID_HEIGHT || board[nx][ny]) return false;
            }
            return true;
        }

        private boolean canMoveWithPiece(int[][] pieceCoords, int posX, int posY) {
            for (int[] cell : pieceCoords) {
                int nx = posX + cell[0], ny = posY + cell[1];
                if (nx < 0 || nx >= GRID_WIDTH || ny < 0 || ny >= GRID_HEIGHT || board[nx][ny]) return false;
            }
            return true;
        }

        private void placePiece() {
            for (int[] cell : currentPiece) {
                int x = currentPiecePos[0] + cell[0];
                int y = currentPiecePos[1] + cell[1];
                if (x >= 0 && x < GRID_WIDTH && y >= 0 && y < GRID_HEIGHT) board[x][y] = true;
            }
        }

        private void clearLines() {
            for (int y = GRID_HEIGHT - 1; y >= 0; y--) {
                boolean full = true;
                for (int x = 0; x < GRID_WIDTH; x++) if (!board[x][y]) {
                    full = false;
                    break;
                }
                if (full) {
                    for (int x = 0; x < GRID_WIDTH; x++) {
                        if (y > 0) System.arraycopy(board[x], 0, board[x], 1, y);
                        board[x][0] = false;
                    }
                    y++; // re-check same y after shift
                }
            }
        }

        @Override
        public void up() { /* noop (use rotate) */ }

        @Override
        public void down() { /* noop maybe soft-drop could be here */ }

        @Override
        public void left() {
            if (canMove(-1, 0)) currentPiecePos[0]--;
        }

        @Override
        public void right() {
            if (canMove(1, 0)) currentPiecePos[0]++;
        }

        @Override
        public void rotate() {
            int newRotation = (currentRotation + 1) % 4;
            int[][] rotated = pieceRotations[currentType][newRotation];
            int[][] kicks = (currentType == 0)
                ? new int[][] { { 0, 0 }, { -1, 0 }, { 1, 0 }, { 0, -1 }, { 0, 1 }, { -2, 0 }, { 2, 0 }, { -3, 0 },
                    { 3, 0 } }
                : new int[][] { { 0, 0 }, { -1, 0 }, { 1, 0 }, { 0, -1 }, { 0, 1 }, { -2, 0 }, { 2, 0 } };
            for (int[] kick : kicks) {
                int tx = currentPiecePos[0] + kick[0], ty = currentPiecePos[1] + kick[1];
                if (canMoveWithPiece(rotated, tx, ty)) {
                    currentPiece = deepClone(rotated);
                    currentPiecePos[0] = tx;
                    currentPiecePos[1] = ty;
                    currentRotation = newRotation;
                    return;
                }
            }
        }

        @Override
        public void action() {
            rotate();
        }

        @Override
        public void hardDrop() {
            while (canMove(0, 1)) currentPiecePos[1]++;
            placePiece();
            clearLines();
            spawnPiece();
        }

        @Override
        public int getUpdateInterval() {
            return Math.max(50, UPDATE_INTERVAL_MS / Math.max(1, dropMultiplier));
        }

        @Override
        public int getWidth() {
            return GRID_WIDTH;
        }

        @Override
        public int getHeight() {
            return GRID_HEIGHT;
        }

        public int getPreviewColor(int x, int y) {
            int[][] piece = pieceRotations[nextType][0];
            for (int[] cell : piece) {
                if (cell[0] == x && cell[1] == y) {
                    return PIECE_COLORS[nextType];
                }
            }
            return C.BG_COLOR_PRIMARY;
        }

        @Override
        public int getCellColor(int x, int y) {
            // placed blocks
            if (board[x][y]) return PLACED_COLOR;

            // ghost piece
            int ghostY = currentPiecePos[1];
            while (canMoveWithPiece(currentPiece, currentPiecePos[0], ghostY + 1)) ghostY++;
            for (int[] cell : currentPiece) {
                int gx = currentPiecePos[0] + cell[0], gy = ghostY + cell[1];
                boolean overlaps = false;
                for (int[] c2 : currentPiece)
                    if (gx == currentPiecePos[0] + c2[0] && gy == currentPiecePos[1] + c2[1]) overlaps = true;
                if (!overlaps && gy >= 0 && gy < GRID_HEIGHT && gx >= 0 && gx < GRID_WIDTH)
                    if (gx == x && gy == y) return GHOST_COLOR;
            }

            // current piece
            for (int[] cell : currentPiece) {
                int cx = currentPiecePos[0] + cell[0], cy = currentPiecePos[1] + cell[1];
                if (cx == x && cy == y) return PIECE_COLORS[currentType];
            }

            return C.BG_COLOR_PRIMARY;
        }
    }

    // ---------------- PhoneApp implementations ----------------
    public class SnakeApp implements PhoneApp {

        @Override
        public String getName() {
            return "Snake";
        }

        @Override
        public GameBase createGame() {
            return new SnakeGame();
        }

        @Override
        public IWidget buildUI(GameBase model, PagedWidget.Controller controller, int pageIndex) {
            SnakeGame snakeGame = (SnakeGame) model;

            Grid grid = new Grid()
                .size(SnakeGame.GRID_WIDTH * C.GRID_CELL_SIZE, SnakeGame.GRID_HEIGHT * C.GRID_CELL_SIZE);

            Widget[][] cells = createGrid(
                grid,
                SnakeGame.GRID_WIDTH,
                SnakeGame.GRID_HEIGHT,
                C.GRID_CELL_SIZE,
                C.BG_COLOR_PRIMARY);

            GridRenderer renderer = new GridRenderer(cells, C.BG_COLOR_PRIMARY);
            List<GameRenderer> renderers = Arrays.asList(renderer);

            GameController gc = new GameController(snakeGame, renderers, controller, ItemPhoneGui.this);

            gc.child(
                new TextWidget<>(IKey.str(getName())).color(C.TEXT_COLOR)
                    .alignment(Alignment.Center)
                    .marginBottom(10))
                .child(
                    new Row().sizeRel(1f, 1f)
                        .mainAxisAlignment(Alignment.MainAxis.CENTER)
                        .crossAxisAlignment(Alignment.CrossAxis.CENTER)
                        .child(grid))
                .background(bgSecondaryRect)
                .margin(5)
                .padding(5);
            return gc;
        }
    }

    private static class PreviewRenderer implements GameRenderer {

        private final Widget[][] cells;
        private final int w, h;
        private final Rectangle bgRect;
        private final int[][] prevColors;

        PreviewRenderer(Widget[][] cells, int bgColor) {
            this.cells = cells;
            this.w = cells.length;
            this.h = cells[0].length;
            this.bgRect = new Rectangle().setColor(bgColor);
            this.prevColors = new int[w][h];
            for (int x = 0; x < w; x++) for (int y = 0; y < h; y++) prevColors[x][y] = bgColor;
        }

        @Override
        public void renderFromModel(GameBase model) {
            if (!(model instanceof TetrisGame t)) return;

            for (int x = 0; x < w; x++) {
                for (int y = 0; y < h; y++) {
                    int color = t.getPreviewColor(x, y);
                    if (prevColors[x][y] != color) {
                        Rectangle rect = new Rectangle().setColor(color);
                        cells[x][y].background(rect);
                        prevColors[x][y] = color;
                    }
                }
            }
        }
    }

    public class TetrisApp implements PhoneApp {

        @Override
        public String getName() {
            return "Tetris";
        }

        @Override
        public GameBase createGame() {
            return new TetrisGame();
        }

        @Override
        public IWidget buildUI(GameBase model, PagedWidget.Controller controller, int pageIndex) {
            TetrisGame tmodel = (TetrisGame) model;

            // --- основное игровое поле ---
            Grid tetrisGrid = new Grid();
            Widget[][] cells = createGrid(
                tetrisGrid,
                TetrisGame.GRID_WIDTH,
                TetrisGame.GRID_HEIGHT,
                C.GRID_CELL_SIZE,
                C.BG_COLOR_PRIMARY);

            int tetrisPxW = TetrisGame.GRID_WIDTH * C.GRID_CELL_SIZE;
            int tetrisPxH = TetrisGame.GRID_HEIGHT * C.GRID_CELL_SIZE;
            tetrisGrid.size(tetrisPxW, tetrisPxH);

            GridRenderer mainRenderer = new GridRenderer(cells, C.BG_COLOR_PRIMARY);

            // --- превью следующей фигуры ---
            Grid previewGrid = new Grid();
            Widget[][] previewCells = createGrid(previewGrid, 4, 4, C.GRID_CELL_SIZE, C.BG_COLOR_PRIMARY);
            previewGrid.size(4 * C.GRID_CELL_SIZE, 4 * C.GRID_CELL_SIZE);
            PreviewRenderer previewRenderer = new PreviewRenderer(previewCells, C.BG_COLOR_PRIMARY);

            // --- список рендереров ---
            List<GameRenderer> renderers = Arrays.asList(mainRenderer, previewRenderer);

            // --- контроллер игры ---
            GameController gc = new GameController(tmodel, renderers, controller, ItemPhoneGui.this);

            // --- колонки и строки для компоновки ---
            Column leftCol = new Column();
            leftCol.size(tetrisPxW, tetrisPxH)
                .child(tetrisGrid);

            Column rightCol = new Column();
            rightCol.size(4 * C.GRID_CELL_SIZE, tetrisPxH)
                .child(
                    new TextWidget<>(IKey.str("Next")).color(C.TEXT_COLOR)
                        .alignment(Alignment.Center)
                        .marginBottom(4))
                .child(previewGrid);

            Row row = new Row();
            row.mainAxisAlignment(Alignment.MainAxis.START)
                .crossAxisAlignment(Alignment.CrossAxis.START)
                .childPadding(12)
                .child(leftCol)
                .child(rightCol);

            // --- финальная компоновка ---
            gc.child(
                new TextWidget<>(IKey.str(getName())).color(C.TEXT_COLOR)
                    .alignment(Alignment.Center)
                    .marginBottom(6))
                .child(row)
                .background(bgSecondaryRect)
                .margin(5)
                .padding(5);

            return gc;
        }
    }

    // ---------------- helper to create Grid of Widget[][] ----------------
    private static Widget[][] createGrid(Grid grid, int w, int h, int cellSize, int bgColor) {
        Widget[][] result = new Widget[w][h];
        for (int y = 0; y < h; y++) {
            grid.nextRow();
            for (int x = 0; x < w; x++) {
                Widget cell = new Widget<>().size(cellSize, cellSize)
                    .background(new Rectangle().setColor(bgColor));
                result[x][y] = cell;
                grid.child(cell);
            }
        }
        return result;
    }
}
