package com.example.catalog.tests.extra;

import static androidx.compose.ui.test.ActionsKt.performClick;
import static androidx.compose.ui.test.AssertionsKt.assertIsDisplayed;
import static androidx.compose.ui.test.AssertionsKt.assertTextEquals;
import static androidx.compose.ui.test.FindersKt.onNodeWithTag;
import static com.example.catalog.support.AppRobotKt.waitForTag;
import static org.junit.Assert.assertEquals;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.example.catalog.support.BaseTest;
import com.example.catalog.support.Fixtures;
import com.example.catalog.support.Responses;
import com.example.catalog.ui.Tags;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Тест на Java поверх тех же помощников, что и тесты на Kotlin. */
@RunWith(AndroidJUnit4.class)
public class JavaStyleTest extends BaseTest {
    private static final long WAIT = 30_000L;
    private static final long DELAY = 2_000L;

    @Before
    public void serve() {
        getRoutes().on("items", (request, call) -> Responses.INSTANCE.items(Fixtures.INSTANCE.items(4, 1), DELAY));
        for (int i = 1; i <= 4; i++) {
            final int id = i;
            getRoutes().on("items/" + id,
                (request, call) -> Responses.INSTANCE.details(Fixtures.INSTANCE.details(id, true), DELAY));
        }
        getApp().launch();
        waitForTag(getCompose(), Tags.INSTANCE.item(1), WAIT);
    }

    private void open(int id) {
        performClick(onNodeWithTag(getCompose(), Tags.INSTANCE.item(id), false));
        waitForTag(getCompose(), Tags.DETAILS_TITLE, WAIT);
    }

    @Test
    public void listShowsAllItems() {
        waitForTag(getCompose(), Tags.INSTANCE.item(4), WAIT);
        assertIsDisplayed(onNodeWithTag(getCompose(), Tags.LIST, false));
    }

    @Test
    public void opensDetails() {
        open(2);
        assertTextEquals(onNodeWithTag(getCompose(), Tags.DETAILS_TITLE, false), new String[] {"Item 2"}, false);
    }

    @Test
    public void detailsShowPrice() {
        open(4);
        assertTextEquals(onNodeWithTag(getCompose(), Tags.DETAILS_PRICE, false), new String[] {"$42.00"}, false);
    }

    @Test
    public void detailsRequestIsSentOnce() {
        open(3);
        assertEquals(1, getRoutes().callsTo("items/3"));
    }
}
