package in.dragonbra.javasteam.util.event;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
class EventTest {
    private Event<EventArgs> event;
    private EventHandler<EventArgs> handler;

    @BeforeEach
    void setUp() {
        event = new Event<>();
        handler = mock(EventHandler.class);
    }

    @Test
    void addEventHandler() {
        event.addEventHandler(handler);
        event.handleEvent(this, EventArgs.EMPTY);

        verify(handler, times(1)).handleEvent(this, EventArgs.EMPTY);
    }

    @Test
    void removeEventHandler() {
        event.addEventHandler(handler);
        event.removeEventHandler(handler);

        event.handleEvent(this, EventArgs.EMPTY);

        verify(handler, never()).handleEvent(this, EventArgs.EMPTY);
    }

    @Test
    void handleEventWithMultipleHandlers() {
        EventHandler<EventArgs> anotherHandler = mock(EventHandler.class);

        event.addEventHandler(handler);
        event.addEventHandler(anotherHandler);

        event.handleEvent(this, EventArgs.EMPTY);

        verify(handler, times(1)).handleEvent(this, EventArgs.EMPTY);
        verify(anotherHandler, times(1)).handleEvent(this, EventArgs.EMPTY);
    }
}
