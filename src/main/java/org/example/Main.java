package org.example;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class Main {

    private static final Duration WAIT_TIME = Duration.ofSeconds(10);
    private static final Duration POLL = Duration.ofMillis(100);
    private static final Duration SHORT_WAIT = Duration.ofSeconds(3);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public static void main(String[] args) {
        WebDriverManager.chromedriver().setup();
        WebDriver driver = new ChromeDriver();

        WebDriverWait wait = new WebDriverWait(driver, WAIT_TIME);
        wait.pollingEvery(POLL);

        try {
            driver.manage().window().maximize();
            driver.get("https://www.booking.com/");

            dismissOverlaysQuick(driver);
            hardHideCommonOverlays(driver);

            // Destination
            WebElement destinationInput = wait.until(
                    ExpectedConditions.presenceOfElementLocated(By.cssSelector("input[name='ss']"))
            );
            focusElement(driver, destinationInput);
            clearAndType(driver, destinationInput, "Goa, India");

            // Autocomplete (SHORT wait only)
            try {
                WebDriverWait shortWait = new WebDriverWait(driver, SHORT_WAIT);
                shortWait.pollingEvery(POLL);

                List<WebElement> options = shortWait.until(
                        ExpectedConditions.visibilityOfAllElementsLocatedBy(
                                By.cssSelector("li[data-testid='autocomplete-result']")
                        )
                );

                boolean clicked = false;
                for (WebElement op : options) {
                    if (op.getText().toLowerCase().contains("goa")) {
                        jsClick(driver, op);
                        clicked = true;
                        break;
                    }
                }
                if (!clicked) destinationInput.sendKeys(Keys.ENTER);

            } catch (TimeoutException ignored) {
                destinationInput.sendKeys(Keys.ENTER);
            }

            // Dates
            LocalDate today = LocalDate.now();
            LocalDate checkIn = today.plusDays(10);
            LocalDate checkOut = today.plusDays(13);

            openCalendarFast(driver, wait);
            switchToCalendarTabFast(driver);
            pickDateFast(driver, wait, checkIn);
            pickDateFast(driver, wait, checkOut);

            // Search
            WebElement searchBtn = wait.until(ExpectedConditions.elementToBeClickable(
                    By.cssSelector("button[type='submit'][data-testid='searchbox-submit-button'], button[type='submit']")
            ));
            jsClick(driver, searchBtn);

            // Wait results
            wait.until(ExpectedConditions.visibilityOfElementLocated(
                    By.cssSelector("div[data-testid='property-card']")
            ));

            // ---------------- APPLY FILTERS ----------------
            // 4 stars and above
            clickFilterIfPresent(driver, wait,
                    By.cssSelector("input[name='class=4'], input[name='class=5']"),
                    By.xpath("//div[@data-filters-group='class']//span[contains(.,'4 stars')]"),
                    By.xpath("//div[@data-filters-group='class']//span[contains(.,'4')]")
            );

            // Breakfast included
            clickFilterIfPresent(driver, wait,
                    By.cssSelector("input[name='mealplan=1']"),
                    By.xpath("//div[contains(@data-testid,'filters-group')]//span[contains(translate(.,'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),'breakfast')]"),
                    By.xpath("//span[contains(translate(.,'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),'breakfast included')]")
            );

            // Free cancellation
            clickFilterIfPresent(driver, wait,
                    By.cssSelector("input[name='fc=1']"),
                    By.xpath("//div[contains(@data-testid,'filters-group')]//span[contains(translate(.,'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),'free cancellation')]"),
                    By.xpath("//span[contains(translate(.,'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),'free cancellation')]")
            );

            wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("div[data-testid='property-card']")));

            // PRINT RESULTS WITH FLAGS
            List<WebElement> hotels = driver.findElements(By.cssSelector("div[data-testid='property-card']"));
            System.out.println("Hotels Found (after filters attempt): " + hotels.size());

            for (WebElement hotel : hotels) {
                String name = getTextSafe(hotel, By.cssSelector("div[data-testid='title']"), "N/A");
                String price = getTextSafe(hotel, By.cssSelector("span[data-testid='price-and-discounted-price']"), "Price not visible");
                String rating = getTextSafe(hotel,
                        By.cssSelector("div[aria-label*='Scored'], div[data-testid='review-score']"),
                        "Rating not available"
                );
                String img = getAttrSafe(hotel, By.cssSelector("img"), "src", "Image not found");

                String cardText = "";
                try { cardText = hotel.getText().toLowerCase(); } catch (Exception ignored) {}

                boolean breakfastIncluded = cardText.contains("breakfast included") || cardText.contains("breakfast");
                boolean freeCancellation = cardText.contains("free cancellation");

                System.out.println("Hotel: " + name);
                System.out.println("Price: " + price);
                System.out.println("Rating: " + rating);
                System.out.println("Breakfast Included: " + (breakfastIncluded ? "YES" : "NO/NOT SHOWN"));
                System.out.println("Free Cancellation: " + (freeCancellation ? "YES" : "NO/NOT SHOWN"));
                System.out.println("Image: " + img);
                System.out.println("----------------------------------");
            }


            // Validate Filtered Results (>= 3)

            int countAfterFilters = driver.findElements(By.cssSelector("div[data-testid='property-card']")).size();
            if (countAfterFilters >= 3) {
                System.out.println("Validation PASSED: at least 3 hotels after filters. Count=" + countAfterFilters);
            } else {
                System.out.println("Validation FAILED: less than 3 hotels after filters. Count=" + countAfterFilters);
            }

            // Select First Hotel -> handle new tab/window
            String parentWindow = driver.getWindowHandle();

            WebElement firstHotelLink = wait.until(ExpectedConditions.elementToBeClickable(
                    By.cssSelector("div[data-testid='property-card'] a[data-testid='title-link'], div[data-testid='property-card'] a")
            ));

            // open in new tab reliably (instead of jsClick)
            openInNewTab(driver, firstHotelLink);

            // switch to new tab if opened
            switchToNewWindow(driver, parentWindow);

            // 8) On Hotel Detail Page:
            //    - Fetch room types and prices (FIXED)
            //    - Scroll bottom and validate Reviews or Policies
            //    - Screenshot proof
            wait.until(ExpectedConditions.presenceOfElementLocated(By.tagName("body")));
            Thread.sleep(800);

            System.out.println("\n=========== HOTEL DETAIL PAGE ===========");
            System.out.println("URL: " + driver.getCurrentUrl());

            dismissOverlaysQuick(driver);
            hardHideCommonOverlays(driver);

            // GO TO AVAILABILITY / ROOM TABLE (important!)
            goToAvailabilitySection(driver, wait);

            // EXTRACT ROOMS + PRICES (works across both layouts)
            List<RoomInfo> rooms = extractRoomsAndPrices(driver);
            if (rooms.isEmpty()) {
                System.out.println("Rooms not detected. Taking debug screenshot...");
                takeScreenshot(driver, "rooms_not_found.png");
                System.out.println("Screenshot saved: rooms_not_found.png");
            } else {
                System.out.println("Rooms Found: " + rooms.size());
                for (RoomInfo r : rooms) {
                    System.out.println("Room: " + r.roomType);
                    System.out.println("Price: " + r.price);
                    System.out.println("------------------------------");
                }
            }

            // Scroll to bottom and validate Reviews or Policies section
            ((JavascriptExecutor) driver).executeScript("window.scrollTo(0, document.body.scrollHeight);");
            Thread.sleep(1200);

            boolean reviewsVisible = isPresentDisplayed(driver,
                    By.cssSelector("#hotel_reviews, [data-testid*='reviews'], [id*='reviews']")
            );
            boolean policiesVisible = isPresentDisplayed(driver,
                    By.cssSelector("#hotel_policy, #hotelPolicies, [data-testid*='policies'], [id*='policy']")
            );

            if (reviewsVisible || policiesVisible) {
                System.out.println("Reviews/Policies section is visible.");
            } else {
                System.out.println("Reviews/Policies section not found (depends on hotel page layout).");
            }

            // Screenshot proof
            takeScreenshot(driver, "final_step.png");
            System.out.println("Screenshot saved: final_step.png");

        } catch (Exception e) {
            System.out.println("Test Failed: " + e.getMessage());
            try {
                takeScreenshot(driver, "error.png");
                System.out.println("Screenshot saved: error.png");
            } catch (Exception ignored) {}
        } finally {
            driver.quit();
        }
    }

    // ---------------- FIXED ROOM EXTRACTION ----------------

    private static void goToAvailabilitySection(WebDriver driver, WebDriverWait wait) {
        // Try clicking a "See availability" / "Reserve" button if present
        clickAnyIfPresent(driver,
                By.xpath("//a[contains(translate(.,'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),'see availability')]"),
                By.xpath("//button[contains(translate(.,'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),'see availability')]"),
                By.xpath("//a[contains(translate(.,'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),'reserve')]"),
                By.xpath("//button[contains(translate(.,'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),\"i'll reserve\")]"),
                By.xpath("//button[contains(translate(.,'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),'reserve')]")
        );

        // Jump to availability anchor (very common on booking pages)
        try {
            ((JavascriptExecutor) driver).executeScript("window.location.hash='availability';");
        } catch (Exception ignored) {}

        // Scroll a bit and wait for "Room type" header or a table section
        for (int i = 0; i < 8; i++) {
            try {
                hardHideCommonOverlays(driver);

                List<WebElement> roomTypeHeaders = driver.findElements(
                        By.xpath("//*[normalize-space()='Room type' or contains(normalize-space(),'Room type')]")
                );
                if (!roomTypeHeaders.isEmpty()) {
                    ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'});", roomTypeHeaders.get(0));
                    return;
                }

                // Sometimes content loads only after scroll
                ((JavascriptExecutor) driver).executeScript("window.scrollBy(0, 900);");
                Thread.sleep(400);
            } catch (Exception ignored) {}
        }

        // Final: wait for any element that indicates availability block exists
        try {
            wait.until(ExpectedConditions.presenceOfElementLocated(
                    By.xpath("//*[contains(.,'Room type') and (self::div or self::table or self::section)]")
            ));
        } catch (Exception ignored) {}
    }

    private static List<RoomInfo> extractRoomsAndPrices(WebDriver driver) {
        // Strategy:
        // 1) Find the availability container by locating the "Room type" header
        // 2) From that container, collect room-type text + nearest price "₹"
        // This works even when Booking changes classes/testids.

        WebElement container = null;

        List<WebElement> headers = driver.findElements(
                By.xpath("//*[normalize-space()='Room type' or contains(normalize-space(),'Room type')]")
        );
        if (!headers.isEmpty()) {
            // go up to a big container that likely contains the whole table/cards
            container = closest(headers.get(0),
                    By.xpath("ancestor::section[1]"),
                    By.xpath("ancestor::div[1]"),
                    By.xpath("ancestor::div[2]"),
                    By.xpath("ancestor::div[3]")
            );
        }

        if (container == null) container = driver.findElement(By.tagName("body"));

        // Collect room name candidates
        // These cover BOTH your screenshots: blue room links + room headers
        List<WebElement> roomNameEls = container.findElements(By.xpath(
                ".//a[normalize-space()!='' and (contains(@href,'#') or contains(@href,'room') or contains(@href,'h') )]" +
                        "[contains(@class,'hprt') or contains(@class,'room') or contains(@class,'bui') or contains(@style,'color') or true()]" +
                        "| .//h3[normalize-space()!='']" +
                        "| .//h2[normalize-space()!='']"
        ));

        // Deduplicate by text, and pair with a price found in the same row/card
        List<RoomInfo> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (WebElement nameEl : roomNameEls) {
            String name = safeText(nameEl);
            if (name.isBlank()) continue;

            // Avoid junk headers
            String low = name.toLowerCase();
            if (low.equals("room type") || low.contains("filter by") || low.contains("your choices") || low.contains("price for"))
                continue;

            if (seen.contains(name)) continue;

            WebElement rowOrCard = closest(nameEl,
                    By.xpath("ancestor::tr[1]"),
                    By.xpath("ancestor::div[contains(.,'₹')][1]"),
                    By.xpath("ancestor::div[1]"),
                    By.xpath("ancestor::div[2]")
            );

            String price = "";
            if (rowOrCard != null) {
                // Most reliable: any element containing ₹ within this row/card
                List<WebElement> priceEls = rowOrCard.findElements(By.xpath(
                        ".//*[contains(normalize-space(),'₹')]"
                ));
                if (!priceEls.isEmpty()) {
                    price = normalizePrice(priceEls.get(0).getText());
                }

                // Also try Booking price testids/classes (sometimes text not directly '₹' in same node)
                if (price.isBlank() || price.equalsIgnoreCase("N/A")) {
                    price = safeTextFrom(rowOrCard,
                            By.cssSelector("span[data-testid='price-and-discounted-price']"),
                            By.cssSelector("span[data-testid='price']"),
                            By.cssSelector("span[class*='prco']"),
                            By.cssSelector("div[data-testid*='price']")
                    );
                    price = normalizePrice(price);
                }
            }

            seen.add(name);
            out.add(new RoomInfo(name, price.isBlank() ? "Price not found" : price));

            if (out.size() >= 12) break; // keep output clean/fast
        }

        return out;
    }

    private static String normalizePrice(String s) {
        if (s == null) return "";
        String t = s.replace("\n", " ").replaceAll("\\s+", " ").trim();
        int idx = t.indexOf("₹");
        if (idx >= 0) {
            String sub = t.substring(idx).trim();
            // stop at double spaces or "tax" if present
            int tax = sub.toLowerCase().indexOf("tax");
            if (tax > 0) sub = sub.substring(0, tax).trim();
            return sub;
        }
        return t;
    }

    private static WebElement closest(WebElement start, By... candidates) {
        for (By by : candidates) {
            try {
                List<WebElement> els = start.findElements(by);
                if (!els.isEmpty()) return els.get(0);
            } catch (Exception ignored) {}
        }
        return null;
    }

    private static String safeText(WebElement el) {
        try {
            return el.getText() == null ? "" : el.getText().trim();
        } catch (Exception e) {
            return "";
        }
    }

    private static String safeTextFrom(WebElement root, By... locators) {
        for (By by : locators) {
            try {
                List<WebElement> els = root.findElements(by);
                if (!els.isEmpty()) {
                    String t = els.get(0).getText();
                    if (t != null && !t.trim().isBlank()) return t.trim();
                }
            } catch (Exception ignored) {}
        }
        return "";
    }

    private static class RoomInfo {
        String roomType;
        String price;
        RoomInfo(String roomType, String price) {
            this.roomType = roomType;
            this.price = price;
        }
    }

    // ---------------- FILTER CLICK HELPERS ----------------

    private static void clickFilterIfPresent(WebDriver driver, WebDriverWait wait, By... locators) {
        for (By by : locators) {
            try {
                List<WebElement> els = driver.findElements(by);
                if (!els.isEmpty()) {
                    WebElement el = els.get(0);

                    if ("input".equalsIgnoreCase(el.getTagName())) {
                        try {
                            String id = el.getAttribute("id");
                            if (id != null && !id.isBlank()) {
                                WebElement label = driver.findElement(By.cssSelector("label[for='" + id + "']"));
                                jsClick(driver, label);
                            } else {
                                jsClick(driver, el);
                            }
                        } catch (Exception e) {
                            jsClick(driver, el);
                        }
                    } else {
                        jsClick(driver, el);
                    }

                    waitSmallDomUpdate(driver);
                    return;
                }
            } catch (Exception ignored) {}
        }
        System.out.println("Filter not found for locators: " + locators.length);
    }

    private static void waitSmallDomUpdate(WebDriver driver) {
        try { Thread.sleep(700); } catch (InterruptedException ignored) {}
    }

    // ---------------- FAST OVERLAY HANDLING ----------------

    private static void dismissOverlaysQuick(WebDriver driver) {
        clickIfPresent(driver, By.id("onetrust-accept-btn-handler"));
        clickIfPresent(driver, By.cssSelector("button[data-testid='cookie-policy-dialog-accept-button']"));
        clickIfPresent(driver, By.cssSelector("button[aria-label='Dismiss']"));
        clickIfPresent(driver, By.cssSelector("button[aria-label='Close']"));
        try { new Actions(driver).moveByOffset(5, 5).click().perform(); } catch (Exception ignored) {}
    }

    private static void hardHideCommonOverlays(WebDriver driver) {
        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;
            js.executeScript(
                    "document.querySelectorAll(" +
                            "'div[role=\"dialog\"], div[role=\"alertdialog\"], " +
                            "div[class*=\"backdrop\"], div[class*=\"overlay\"], div[class*=\"modal\"], " +
                            "div[aria-modal=\"true\"], div[data-testid*=\"modal\"], div[data-testid*=\"overlay\"], " +
                            "div.bbe73dce14'" +
                            ").forEach(e => { e.style.display='none'; e.style.visibility='hidden'; });"
            );
        } catch (Exception ignored) {}
    }

    private static void focusElement(WebDriver driver, WebElement el) {
        try {
            ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'});", el);
            ((JavascriptExecutor) driver).executeScript("arguments[0].focus();", el);
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", el);
        } catch (Exception ignored) {}
    }

    private static void clearAndType(WebDriver driver, WebElement el, String text) {
        try {
            el.sendKeys(Keys.chord(Keys.CONTROL, "a"));
            el.sendKeys(Keys.BACK_SPACE);
        } catch (Exception ignored) {
            ((JavascriptExecutor) driver).executeScript("arguments[0].value='';", el);
        }
        el.sendKeys(text);
    }

    private static void jsClick(WebDriver driver, WebElement el) {
        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", el);
    }

    private static void clickIfPresent(WebDriver driver, By by) {
        try {
            List<WebElement> els = driver.findElements(by);
            if (!els.isEmpty() && els.get(0).isDisplayed()) {
                jsClick(driver, els.get(0));
            }
        } catch (Exception ignored) {}
    }

    private static void clickAnyIfPresent(WebDriver driver, By... locators) {
        for (By by : locators) {
            try {
                List<WebElement> els = driver.findElements(by);
                if (!els.isEmpty() && els.get(0).isDisplayed()) {
                    jsClick(driver, els.get(0));
                    Thread.sleep(400);
                    return;
                }
            } catch (Exception ignored) {}
        }
    }

    private static void openInNewTab(WebDriver driver, WebElement el) {
        try {
            // CTRL+ENTER works on links, opens new tab on most browsers
            el.sendKeys(Keys.chord(Keys.CONTROL, Keys.ENTER));
        } catch (Exception e) {
            // fallback
            jsClick(driver, el);
        }
    }

    // ---------------- FAST CALENDAR ----------------

    private static void openCalendarFast(WebDriver driver, WebDriverWait wait) {
        dismissOverlaysQuick(driver);
        hardHideCommonOverlays(driver);

        By opener = By.cssSelector("span[data-testid='date-display-field-start'], button[data-testid='date-display-field-start']");
        WebElement el = wait.until(ExpectedConditions.presenceOfElementLocated(opener));
        jsClick(driver, el);

        wait.until(ExpectedConditions.visibilityOfElementLocated(
                By.cssSelector("div[role='dialog'], [data-testid='searchbox-datepicker-calendar']")
        ));
    }

    private static void switchToCalendarTabFast(WebDriver driver) {
        try {
            WebElement tab = driver.findElement(By.cssSelector("button#calendar-searchboxdatepicker-tab-trigger"));
            jsClick(driver, tab);
        } catch (Exception ignored) {}
    }

    private static void pickDateFast(WebDriver driver, WebDriverWait wait, LocalDate date) {
        String target = date.format(DATE);

        for (int i = 0; i < 10; i++) {
            hardHideCommonOverlays(driver);

            List<WebElement> day = driver.findElements(
                    By.cssSelector("span[data-date='" + target + "'], td[data-date='" + target + "']")
            );
            if (!day.isEmpty()) {
                jsClick(driver, day.get(0));
                return;
            }

            List<WebElement> next = driver.findElements(
                    By.cssSelector("button[aria-label*='Next month'], button[data-testid='calendar-next']")
            );
            if (!next.isEmpty()) {
                jsClick(driver, next.get(0));
                wait.until(ExpectedConditions.visibilityOfElementLocated(
                        By.cssSelector("div[role='dialog'], [data-testid='searchbox-datepicker-calendar']")
                ));
            } else {
                throw new RuntimeException("Next month button not found for date: " + target);
            }
        }
        throw new RuntimeException("Date not found quickly: " + target);
    }

    // ---------------- WINDOW + CHECKS + SCREENSHOT ----------------

    private static void switchToNewWindow(WebDriver driver, String parentWindow) {
        for (int i = 0; i < 25; i++) {
            for (String handle : driver.getWindowHandles()) {
                if (!handle.equals(parentWindow)) {
                    driver.switchTo().window(handle);
                    return;
                }
            }
            try { Thread.sleep(200); } catch (InterruptedException ignored) {}
        }
        driver.switchTo().window(parentWindow);
    }

    private static boolean isPresentDisplayed(WebDriver driver, By by) {
        try {
            List<WebElement> els = driver.findElements(by);
            return !els.isEmpty() && els.get(0).isDisplayed();
        } catch (Exception e) {
            return false;
        }
    }

    private static void takeScreenshot(WebDriver driver, String fileName) throws Exception {
        File src = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
        Files.copy(src.toPath(), Path.of(fileName), StandardCopyOption.REPLACE_EXISTING);
    }

    // ---------------- SAFE GETTERS ----------------

    private static String getTextSafe(WebElement parent, By by, String fallback) {
        try { return parent.findElement(by).getText().trim(); }
        catch (Exception e) { return fallback; }
    }

    private static String getAttrSafe(WebElement parent, By by, String attr, String fallback) {
        try {
            String v = parent.findElement(by).getAttribute(attr);
            return (v == null || v.isBlank()) ? fallback : v;
        } catch (Exception e) {
            return fallback;
        }
    }
}
