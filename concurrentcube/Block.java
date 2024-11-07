package concurrentcube;

public class Block {
    private final int colour;

    public Block(int colour) {
        this.colour = colour;
    }

    @Override
    public String toString() {
        return String.valueOf(colour);
    }
}
