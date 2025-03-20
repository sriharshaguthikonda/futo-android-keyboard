package org.futo.inputmethod.keyboard;

import org.futo.inputmethod.annotations.UsedForTesting;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;

/**
 * KeyboardLayout maintains the keyboard layout information.
 */
public class KeyboardLayout {

    private final int[] mKeyCodes;
    private final int[] mKeyXCoordinates;
    private final int[] mKeyYCoordinates;
    private final int[] mKeyWidths;
    private final int[] mKeyHeights;

    public final int mMostCommonKeyWidth;
    public final int mMostCommonKeyHeight;
    public final int mKeyboardWidth;
    public final int mKeyboardHeight;
    private final int keySpacing; // Key spacing in pixels (default: 2px for slight separation)
    private final int rowSpacing; // Row spacing in pixels (default: 2px to reduce height between rows)

    public KeyboardLayout(ArrayList<Key> layoutKeys, int mostCommonKeyWidth,
                          int mostCommonKeyHeight, int keyboardWidth, int keyboardHeight, int keySpacing, int rowSpacing) {
        this.mMostCommonKeyWidth = mostCommonKeyWidth;
        this.mMostCommonKeyHeight = mostCommonKeyHeight;
        this.mKeyboardWidth = keyboardWidth;
        this.mKeyboardHeight = keyboardHeight;
        this.keySpacing = keySpacing;
        this.rowSpacing = rowSpacing;

        mKeyCodes = new int[layoutKeys.size()];
        mKeyXCoordinates = new int[layoutKeys.size()];
        mKeyYCoordinates = new int[layoutKeys.size()];
        mKeyWidths = new int[layoutKeys.size()];
        mKeyHeights = new int[layoutKeys.size()];

        for (int i = 0; i < layoutKeys.size(); i++) {
            Key key = layoutKeys.get(i);
            mKeyCodes[i] = Character.toLowerCase(key.getCode());
            mKeyXCoordinates[i] = key.getDrawX() + keySpacing; // Apply 2px spacing to X coordinate
            mKeyYCoordinates[i] = key.getY() + rowSpacing; // Apply 2px spacing to Y coordinate to reduce row height
            mKeyWidths[i] = key.getDrawWidth() - keySpacing; // Adjust width to maintain layout
            mKeyHeights[i] = key.getHeight() - rowSpacing; // Adjust height to reduce row height
        }
    }

    @UsedForTesting
    public int[] getKeyCodes() {
        return mKeyCodes;
    }

    public int[] getKeyXCoordinates() {
        return mKeyXCoordinates;
    }

    public int[] getKeyYCoordinates() {
        return mKeyYCoordinates;
    }

    public int[] getKeyWidths() {
        return mKeyWidths;
    }

    public int[] getKeyHeights() {
        return mKeyHeights;
    }

    public static KeyboardLayout newKeyboardLayout(@Nonnull final List<Key> sortedKeys,
                                                   int mostCommonKeyWidth, int mostCommonKeyHeight,
                                                   int occupiedWidth, int occupiedHeight) {
        final int keySpacing = 2; // Default spacing set to 2 pixels
        final int rowSpacing = 2; // Default row spacing set to 2 pixels
        final ArrayList<Key> layoutKeys = new ArrayList<>();
        for (final Key key : sortedKeys) {
            if (!ProximityInfo.needsProximityInfo(key)) {
                continue;
            }
            if (key.getCode() != ',') {
                layoutKeys.add(key);
            }
        }
        return new KeyboardLayout(layoutKeys, mostCommonKeyWidth,
                mostCommonKeyHeight, occupiedWidth, occupiedHeight, keySpacing, rowSpacing);
    }
}
