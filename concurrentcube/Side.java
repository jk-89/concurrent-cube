package concurrentcube;

public class Side {
    private final int layers;
    private final Block[][] blocks;

    public Side(int layers, int colour) {
        this.layers = layers;
        this.blocks = new Block[layers][layers];
        for (int i = 0; i < layers; i++) {
            for (int j = 0; j < layers; j++) {
                blocks[i][j] = new Block(colour);
            }
        }
    }

    public String toStringArray() {
        StringBuilder sb = new StringBuilder();
        for (Block[] layer : blocks) {
            for (Block block : layer) {
                sb.append(block.toString());
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (Block[] layer : blocks) {
            for (Block block : layer) {
                sb.append(block.toString());
            }
        }
        return sb.toString();
    }

    public void rotate90Degrees(boolean clockwise) {
        for (int i = 0; i < layers; i++) {
            for (int j = 0; j < i; j++) {
                Block temp = blocks[i][j];
                blocks[i][j] = blocks[j][i];
                blocks[j][i] = temp;
            }
        }

        for (int i = 0; i < layers; i++) {
            for (int j = 0; j < layers / 2; j++) {
                if (clockwise) {
                    Block temp = blocks[i][j];
                    blocks[i][j] = blocks[i][layers - j - 1];
                    blocks[i][layers - j - 1] = temp;
                }
                else {
                    Block temp = blocks[j][i];
                    blocks[j][i] = blocks[layers - j - 1][i];
                    blocks[layers - j - 1][i] = temp;
                }
            }
        }
    }

    private void swapBlocks(int i1, int j1, int i2, int j2, Side other) {
        Block temp = blocks[i1][j1];
        blocks[i1][j1] = other.blocks[i2][j2];
        other.blocks[i2][j2] = temp;
    }

    public void swapRowWithRow(int row, int otherRow, boolean reversed, Side other) {
        for (int j = 0; j < layers; j++) {
            if (reversed) {
                swapBlocks(row, j, otherRow, layers - j - 1, other);
            } else {
                swapBlocks(row, j, otherRow, j, other);
            }
        }
    }

    public void swapColumnWithColumn(int column, int otherColumn, boolean reversed, Side other) {
        for (int i = 0; i < layers; i++) {
            if (reversed) {
                swapBlocks(i, column, layers - i - 1, otherColumn, other);
            } else {
                swapBlocks(i, column, i, otherColumn, other);
            }
        }
    }

    public void swapRowWithColumn(int row, int column, boolean reversed, Side other) {
        for (int j = 0; j < layers; j++) {
            if (reversed) {
                swapBlocks(row, j, layers - j - 1, column, other);
            } else {
                swapBlocks(row, j, j, column, other);
            }
        }
    }

    public void swapColumnWithRow(int column, int row, boolean reversed, Side other) {
        other.swapRowWithColumn(row, column, reversed, this);
    }
}
