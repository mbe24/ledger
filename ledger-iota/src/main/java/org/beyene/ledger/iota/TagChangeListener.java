package org.beyene.ledger.iota;

import java.util.EventListener;
import java.util.EventObject;
import java.util.Set;

public interface TagChangeListener extends EventListener {

    void tagChanged(TagChangeEvent e);

    class TagChangeEvent extends EventObject {

        private static final long serialVersionUID = 1L;

        private final TagChangeAction action;
        private final String tag;

        public TagChangeEvent(Set<String> source, String tag, TagChangeAction action) {
            super(source);
            this.action = action;
            this.tag = tag;
        }

        public String getTag() {
            return tag;
        }

        public TagChangeAction getAction() {
            return action;
        }

        @SuppressWarnings("unchecked")
        @Override
        public Set<String> getSource() {
            return (Set<String>) source;
        }
    }

    enum TagChangeAction {
        ADD, REMOVE
    }
}


