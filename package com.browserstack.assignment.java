package com.browserstack.assignment;

import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;


public class Assignment1 {

    // Set your Google Translate API key here (or use the free endpoint as in your reference)
    // private static final String GOOGLE_TRANSLATE_API_KEY = "YOUR_GOOGLE_TRANSLATE_API_KEY";

    public static void main(String[] args) {
        // Set path to your ChromeDriver if not in PATH
        // System.setProperty("webdriver.chrome.driver",  "C:\\Users\\pooja lakshmi\\eclipse-workspace\\elpais-scraper\\drivers\\chromedriver.exe");

        WebDriver driver = new ChromeDriver();
        List<String> translatedTitles = new ArrayList<>();
        List<String> originalTitles = new ArrayList<>();
        List<String> articleContents = new ArrayList<>();
        List<String> imagePaths = new ArrayList<>();

        try {
            driver.get("https://elpais.com/opinion/");
            Thread.sleep(3000);

            // Get first 5 unique article URLs
            List<WebElement> linkElements = driver.findElements(By.cssSelector("a[href^='https://elpais.com/opinion/']"));
            Set<String> articleUrls = new LinkedHashSet<>();
            for (WebElement link : linkElements) {
                try {
                    String href = link.getAttribute("href");
                    if (href.contains("/202") && !href.contains("editoriales")) {
                        articleUrls.add(href);
                    }
                } catch (StaleElementReferenceException ignored) {}
                if (articleUrls.size() >= 5) break;
            }

            int idx = 1;
            for (String url : articleUrls) {
                System.out.println("Opening article: " + url);
                driver.get(url);

                try {
                    WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
                    wait.until(webDriver -> ((JavascriptExecutor) webDriver)
                            .executeScript("return document.readyState").equals("complete"));
                    Thread.sleep(2000);

                    // Get title
                    String title = "";
                    try {
                        WebElement h1Elem = wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("h1")));
                        title = h1Elem.getText();
                    } catch (TimeoutException | NoSuchElementException e) {
                        // fallback
                        String[] urlParts = url.split("/");
                        title = urlParts[urlParts.length - 1].replace("-", " ").replace(".html", "");
                    }
                    originalTitles.add(title);
                    System.out.println("Title: " + title);

                    // Translate title
                    String translated = translateText(title);
                    translatedTitles.add(translated);
                    System.out.println("Translated Title: " + translated);

                    // Get content using JSoup
                    Document doc = Jsoup.parse(driver.getPageSource());
                    Elements contentElements = doc.select("div.a_c p, div.article_body p, article p");
                    StringBuilder contentBuilder = new StringBuilder();
                    for (org.jsoup.nodes.Element paragraph : contentElements) {
                        contentBuilder.append(paragraph.text()).append("\n");
                    }
                    String content = contentBuilder.toString().trim();
                    articleContents.add(content);
                    System.out.println("Content:\n" + (content.isEmpty() ? "(No content extracted)" : content.substring(0, Math.min(300, content.length())) + "..."));

                    // Download cover image if available
                    String imagePath = null;
                    try {
                        WebElement imageElement = driver.findElement(By.cssSelector("figure img"));
                        String imageUrl = imageElement.getAttribute("src");
                        if (imageUrl != null && !imageUrl.isEmpty()) {
                            imagePath = "article_" + idx + ".jpg";
                            downloadImage(imageUrl, imagePath);
                            System.out.println("Image saved as: " + imagePath);
                        } else {
                            System.out.println("No image found.");
                        }
                    } catch (NoSuchElementException e) {
                        System.out.println("No image found.");
                    }
                    imagePaths.add(imagePath);

                    System.out.println("\n========================\n");
                    idx++;

                } catch (Exception e) {
                    System.out.println("Error processing article: " + url);
                }
            }

            // Print all original titles and contents
            System.out.println("=== Spanish Titles and Contents ===");
            for (int i = 0; i < originalTitles.size(); i++) {
                System.out.println("Title: " + originalTitles.get(i));
                System.out.println("Content: " + (articleContents.get(i).length() > 300 ? articleContents.get(i).substring(0, 300) + "..." : articleContents.get(i)));
                System.out.println("Image: " + imagePaths.get(i));
                System.out.println("-----");
            }

            // Print translated headers
            System.out.println("=== Translated Headers ===");
            for (String t : translatedTitles) {
                System.out.println(t);
            }

            // Analyze repeated words
            Map<String, Integer> wordCountMap = new HashMap<>();
            for (String tTitle : translatedTitles) {
                String[] words = tTitle.toLowerCase().replaceAll("[^a-z ]", " ").split("\\s+");
                for (String word : words) {
                    if (!word.isEmpty()) {
                        wordCountMap.put(word, wordCountMap.getOrDefault(word, 0) + 1);
                    }
                }
            }
            boolean found = false;
            for (Map.Entry<String, Integer> entry : wordCountMap.entrySet()) {
                if (entry.getValue() > 2) {
                    if (!found) {
                        System.out.println("Repeated words in translated titles (more than twice):");
                        found = true;
                    }
                    System.out.println(entry.getKey() + ": " + entry.getValue());
                }
            }
            if (!found) {
                System.out.println("Count of Repeated Words In Translated Title: 0");
            }

        } catch (Exception e) {
            System.out.println("Fatal error: " + e.getMessage());
        } finally {
            driver.quit();
        }
    }

    // Use Google Translate's free endpoint (for demo/assignment use)
    public static String translateText(String text) {
        try {
            String encodedText = URLEncoder.encode(text, StandardCharsets.UTF_8);
            String urlStr = String.format(
                    "https://translate.googleapis.com/translate_a/single?client=gtx&sl=es&tl=en&dt=t&q=%s", encodedText
            );
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) response.append(line);
            reader.close();
            String result = response.toString();
            int start = result.indexOf("\"");
            int end = result.indexOf("\"", start + 1);
            if (start != -1 && end != -1) {
                return result.substring(start + 1, end);
            }
        } catch (Exception e) {
            System.out.println("Translation failed: " + e.getMessage());
        }
        return "[Translation failed]";
    }

    private static void downloadImage(String imageUrl, String fileName) {
        try (InputStream in = new URL(imageUrl).openStream()) {
            Files.copy(in, new File(fileName).toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            System.out.println("Failed to download image: " + e.getMessage());
        }
    }

}
