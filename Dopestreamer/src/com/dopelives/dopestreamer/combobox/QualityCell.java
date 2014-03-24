package com.dopelives.dopestreamer.combobox;

import com.dopelives.dopestreamer.streamservices.Quality;

/**
 * A combo box cell that shows the label of qualities.
 */
public class QualityCell extends ComboBoxCell<Quality> {
    @Override
    public void updateItem(final Quality item, final boolean empty) {
        super.updateItem(item, empty);

        if (item == null) {
            return;
        }

        setText(item.getLabel());
    }
}