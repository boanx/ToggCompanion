package com.togg.companion.core;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
public class HomeCardLayoutTest {
    private List<String> flatten(List<List<String>> rows) { List<String> keys = new ArrayList<>(); for (List<String> row : rows) keys.addAll(row); return keys; }
    @Test public void wideCardsKeepChosenPositionAmongGridCards() {
        assertEquals(Arrays.asList("location", "tires", "abrp", "battery", "odometer", "drive", "12v"),
                flatten(HomeCardLayout.rows("location,tires,abrp,battery,odometer,drive,12v", HomeCardLayout.DEFAULT_ORDER, 2)));
    }
    @Test public void hiddenWideCardsStayHiddenAndDoNotChangeOrder() {
        assertEquals(Arrays.asList("tires", "battery"), flatten(HomeCardLayout.rows("location,tires,abrp,battery", "tires,battery", 2)));
    }
    @Test public void upgradePreservesHiddenExistingCardsAndAddsOnlyNewCards() {
        assertEquals("battery,odometer,abrp", HomeCardLayout.upgradeVisible("battery,drive,tires,location,12v", "battery"));
        assertEquals("location,battery,drive,tires,12v,odometer,abrp", HomeCardLayout.order("location,battery,drive,tires,12v"));
    }
    @Test public void oneColumnAndTwoColumnsPreserveSameDisplayOrder() {
        String order = "tires,location,battery,drive,abrp,12v,odometer";
        assertEquals(flatten(HomeCardLayout.rows(order, HomeCardLayout.DEFAULT_ORDER, 1)), flatten(HomeCardLayout.rows(order, HomeCardLayout.DEFAULT_ORDER, 2)));
    }
}
