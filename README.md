# Booking.com Hotel Search Automation (Selenium + Java)

This project automates a complete hotel search and booking flow on **Booking.com** using **Selenium WebDriver with Java**.

## Objective

The automation script performs the following:

- Launches Booking.com
- Searches for hotels in **Goa, India**
- Selects dynamic check-in/check-out dates
- Applies filters:
    - 4★ and above
    - Breakfast included
    - Free cancellation
- Extracts hotel listings from the first results page:
    - Hotel Name
    - Price
    - Rating
    - Image URL
- Validates that at least 3 hotels appear after filtering
- Opens the first hotel in a new tab
- Extracts **Room Types and Prices** from the hotel detail page
- Scrolls to bottom and validates **Reviews / Policies** section
- Takes a final screenshot (`final_step.png`)

---

## Prerequisites

Make sure you have the following installed on your machine:

1. **Java JDK 17 or above**
2. **Maven**
3. **Google Chrome (latest version recommended)**
4. **IntelliJ IDEA (or any Java IDE)**

---

## Dependencies (Maven)

Ensure your `pom.xml` contains at least the following dependencies:

```xml
<dependencies>
    <dependency>
        <groupId>org.seleniumhq.selenium</groupId>
        <artifactId>selenium-java</artifactId>
        <version>4.17.0</version>
    </dependency>

    <dependency>
        <groupId>io.github.bonigarcia</groupId>
        <artifactId>webdrivermanager</artifactId>
        <version>5.7.0</version>
    </dependency>

    <dependency>
        <groupId>org.testng</groupId>
        <artifactId>testng</artifactId>
        <version>7.9.0</version>
        <scope>test</scope>
    </dependency>
</dependencies>

