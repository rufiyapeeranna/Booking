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

            // Autocomplete
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
            clickFilterIfPresent(driver, wait,
                    By.cssSelector("input[name='class=4'], input[name='class=5']"),
                    By.xpath("//div[@data-filters-group='class']//span[contains(.,'4 stars')]"),
                    By.xpath("//div[@data-filters-group='class']//span[contains(.,'4')]")
            );

            clickFilterIfPresent(driver, wait,
                    By.cssSelector("input[name='mealplan=1']"),
                    By.xpath("//div[contains(@data-testid,'filters-group')]//span[contains(translate(.,'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),'breakfast')]"),
                    By.xpath("//span[contains(translate(.,'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),'breakfast included')]")
            );

            clickFilterIfPresent(driver, wait,
                    By.cssSelector("input[name='fc=1']"),
                    By.xpath("//div[contains(@data-testid,'filters-group')]//span[contains(translate(.,'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),'free cancellation')]"),
                    By.xpath("//span[contains(translate(.,'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),'free cancellation')]")
            );

            wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("div[data-testid='property-card']")));

            // ---------------- PRINT RESULTS WITH FLAGS ----------------
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

            // 6) Validate >=3
            int countAfterFilters = driver.findElements(By.cssSelector("div[data-testid='property-card']")).size();
            if (countAfterFilters >= 3) {
                System.out.println("✅ Validation PASSED: at least 3 hotels after filters. Count=" + countAfterFilters);
            } else {
                System.out.println("❌ Validation FAILED: less than 3 hotels after filters. Count=" + countAfterFilters);
            }

            // 7) Open first hotel in new tab
            String parentWindow = driver.getWindowHandle();

            WebElement firstHotelLink = wait.until(ExpectedConditions.elementToBeClickable(
                    By.cssSelector("div[data-testid='property-card'] a[data-testid='title-link'], div[data-testid='property-card'] a")
            ));
            openInNewTab(driver, firstHotelLink);
            switchToNewWindow(driver, parentWindow);

            // 8) Detail page - rooms/prices + reviews/policies + screenshot
            wait.until(ExpectedConditions.presenceOfElementLocated(By.tagName("body")));
            Thread.sleep(800);

            System.out.println("\n=========== HOTEL DETAIL PAGE ===========");
            System.out.println("URL: " + driver.getCurrentUrl());

            dismissOverlaysQuick(driver);
            hardHideCommonOverlays(driver);

            // IMPORTANT: scroll to availability / room table
            goToAvailabilitySection(driver);

            // ✅ REAL ROOM TABLE EXTRACTION (only availability rows)
            List<RoomInfo> rooms = extractRoomsFromAvailabilityTable(driver);
            if (rooms.isEmpty()) {
                System.out.println("❌ Rooms not found in availability table. Taking screenshot for debug...");
                takeScreenshot(driver, "rooms_not_found.png");
                System.out.println("✅ Screenshot saved: rooms_not_found.png");
            } else {
                System.out.println("✅ Rooms Found: " + rooms.size());
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
                System.out.println("✅ Reviews/Policies section is visible.");
            } else {
                System.out.println("⚠️ Reviews/Policies section not found (depends on hotel page layout).");
            }

            takeScreenshot(driver, "final_step.png");
            System.out.println("✅ Screenshot saved: final_step.png");

        } catch (Exception e) {
            System.out.println("❌ Test Failed: " + e.getMessage());
            try {
                takeScreenshot(driver, "error.png");
                System.out.println("✅ Screenshot saved: error.png");
            } catch (Exception ignored) {}
        } finally {
            driver.quit();
        }
    }

    // ================== FIXED: ONLY AVAILABILITY TABLE ==================

    private static void goToAvailabilitySection(WebDriver driver) {
        // best: jump to availability anchor
        try { ((JavascriptExecutor) driver).executeScript("window.location.hash='availability';"); } catch (Exception ignored) {}

        // scroll until room table header appears
        for (int i = 0; i < 10; i++) {
            try {
                hardHideCommonOverlays(driver);

                // if table exists, stop
                if (!driver.findElements(By.id("hprt-table")).isEmpty()) return;

                // header "Room type" present => table area loaded
                if (!driver.findElements(By.xpath("//*[normalize-space()='Room type']")).isEmpty()) return;

                ((JavascriptExecutor) driver).executeScript("window.scrollBy(0, 900);");
                Thread.sleep(450);
            } catch (Exception ignored) {}
        }
    }

    private static List<RoomInfo> extractRoomsFromAvailabilityTable(WebDriver driver) {
        WebElement table = null;

        // 1) Most common id
        List<WebElement> t1 = driver.findElements(By.cssSelector("#hprt-table"));
        if (!t1.isEmpty()) table = t1.get(0);

        // 2) Fallback: any table that contains "Room type"
        if (table == null) {
            List<WebElement> candidates = driver.findElements(By.xpath("//table[.//*[normalize-space()='Room type']]"));
            if (!candidates.isEmpty()) table = candidates.get(0);
        }

        if (table == null) return Collections.emptyList();

        // rows
        List<WebElement> rows = table.findElements(By.cssSelector("tbody tr"));
        if (rows.isEmpty()) rows = table.findElements(By.cssSelector("tr"));

        List<RoomInfo> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (WebElement row : rows) {
            String roomType = "";

            // room type link (like in your screenshot)
            roomType = safeTextFrom(row,
                    By.cssSelector("a.hprt-roomtype-link"),
                    By.cssSelector("a[href*='room']"),
                    By.cssSelector("td:first-child a"),
                    By.cssSelector("th:first-child a")
            );

            // if still empty, skip (prevents header/nav)
            if (roomType.isBlank()) continue;

            // PRICE: usually in price column with prco-*
            String price = safeTextFrom(row,
                    By.cssSelector("span.prco-valign-middle-helper"),
                    By.cssSelector("span[class*='prco']"),
                    By.cssSelector("[data-testid*='price']")
            );

            // fallback: any text in row containing ₹
            if (price.isBlank()) {
                List<WebElement> rupee = row.findElements(By.xpath(".//*[contains(normalize-space(),'₹')]"));
                if (!rupee.isEmpty()) price = rupee.get(0).getText().trim();
            }

            price = normalizePrice(price);

            if (!seen.contains(roomType)) {
                out.add(new RoomInfo(roomType, price.isBlank() ? "Price not found" : price));
                seen.add(roomType);
            }
        }

        return out;
    }

    private static String normalizePrice(String s) {
        if (s == null) return "";
        String t = s.replace("\n", " ").replaceAll("\\s+", " ").trim();
        int idx = t.indexOf("₹");
        if (idx >= 0) {
            String sub = t.substring(idx).trim();
            int tax = sub.toLowerCase().indexOf("tax");
            if (tax > 0) sub = sub.substring(0, tax).trim();
            return sub;
        }
        return t;
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

    private static void openInNewTab(WebDriver driver, WebElement el) {
        try {
            el.sendKeys(Keys.chord(Keys.CONTROL, Keys.ENTER));
        } catch (Exception e) {
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
}
