import java.time.Duration;
import java.util.List;

import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class Dashboard {

	static final int WAIT_TIMEOUT = 30;

	static final String LOST_MODE_MESSAGE = "This device is locked! Please reserve it first or contact the administrator.";

	static WebDriver driver;
	static WebDriverWait wait;

	public static void main(String[] args) throws InterruptedException {
		// TODO Auto-generated method stub
		try {
			String driverPath = "/Users/siddaharth.suman/Downloads/Selenium/chromedriver";
			System.setProperty("webdriver.chrome.driver", driverPath);
			driver = new ChromeDriver();
			wait = new WebDriverWait(driver, WAIT_TIMEOUT);
			
			

			login();

			openFindIphone();

			String[] devices = { "PwC IPhone i14", "PwC IPhone i9", "PwC IPhone i8", "PwC IPhone i7" };

			for (int i = 0; i < 1; i++) {
				System.out.println("===============Start of report #" + (i + 1) + "================");
				// for (String device : devices) {
					// System.out.println("Checking status for device: " + device);

					wait.until(ExpectedConditions
							.elementToBeClickable(By.xpath("//div[contains(@title, \"Show devices using\")]"))).click();

					// handle stale element exception here
					boolean handled = false;
					do {
						try {
//							wait.until(ExpectedConditions
//									.elementToBeClickable(By.xpath("//div[@title=\"" + device + "\"]"))).click();
							wait.until(ExpectedConditions
									.elementToBeClickable(By.xpath("//div[@title=\"" + "PwC IPhone i9" + "\"]"))).click();
							handled = true;
						} catch (StaleElementReferenceException e) {
							System.out.println("Handling stale element reference exception");
							handled = false;
						}
					} while (!handled);
					// Check maybe if device is offline and pending or pending lost mode or already
					// in lost mode

					if (!checkIfAlreadyInLostMode()) {
						System.out.println("Device not in lost mode");
						if (!checkIfLostModePending()) {
							System.out.println("Device not in pending mode");
							// Check if device is offline for a while
							if (checkIfDeviceOffline()) {
								System.out.println("Device is offline");
								// Report state of the device to server as Offline without Pending
								// Put device in lock state
							} else {
								System.out.println("Device is online");
								// Report state of the device to server as Online without Pending
								// Put device in lock state
							}
						} else {
							System.out.println("Device is in pending mode");
							if (checkIfDeviceOffline()) {
								System.out.println("Device is offline and pending");
								// Report state of the device to server as Offline with Pending
							} else {
								System.out.println("Device is online and pending");
								// Report state of the device to server as Online with Pending
							}
						}
					} else {
						System.out.println("Device is in lost mode");
						// Report state of the device to server as in Lost Mode
					}
				// }
			}
			// Thread.sleep(5000);
		} catch (TimeoutException e) {
			System.out.println("Timeout exception");
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (driver != null) {
				logout();
				driver.quit();
			}
		}
	}

	static boolean checkIfLostModePending() {
		try {
			// wait.until(ExpectedConditions.visibilityOfAllElementsLocatedBy(By.xpath("//div[text()=\"Lost
			// Mode Pending\"]")));
			List<WebElement> lostModeDivs = driver.findElements(By.xpath("//div[text()=\"Lost Mode Pending\"]"));
			boolean isDeviceInPendingState = false;
			for (WebElement div : lostModeDivs) {
				if (div.isDisplayed()) {
					isDeviceInPendingState = true;
					break;
				}
			}

			return isDeviceInPendingState;
		} catch (Exception e) {
			System.out.println("Could not find lost mode pending text");
			e.printStackTrace();
			return false;
		}
	}

	static boolean checkIfDeviceOffline() {
		try {
			try {
				// Check if green/black location dot is present
				wait.withTimeout(Duration.ofSeconds(1)).until(ExpectedConditions
						.visibilityOfElementLocated(By.xpath("//div[contains(@class, \'device-annotation\')]")));

				WebElement locationDot = driver.findElement(By.xpath("//div[contains(@class, \'device-annotation\')]"));
				if (locationDot.getAttribute("class").contains("offline")) {
					System.out.println("Device was offline recently");
				} else {
					System.out.println("Device is online");
				}
				return false;
			} catch (Exception e) {
				System.out.println("Could not find the location dot");
				// e.printStackTrace();
			} finally {
				// reset the modified timeout
				wait.withTimeout(Duration.ofSeconds(WAIT_TIMEOUT));
			}

			wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//label[@title=\"Offline\"]")));
			List<WebElement> offlineLabels = driver.findElements(By.xpath("//label[@title=\"Offline\"]"));
			if (offlineLabels.size() > 1) {
				System.out.println("There are multiple offline labels");
			}

			boolean isDeviceOffline = false;
			for (WebElement label : offlineLabels) {
				if (label.isDisplayed()) {
					isDeviceOffline = true;
					break;
				}
			}
			return isDeviceOffline;
		} catch (Exception e) {
			System.out.println("Error while finding offline label");
			e.printStackTrace();
			return false;
		}
	}

	static void putDeviceInLostMode() throws Exception {
		WebElement lostModeButton = getActiveLostModeButton();
		if (lostModeButton == null)
			throw new Exception("No active lost mode buttons found!");

		wait.until(ExpectedConditions.elementToBeClickable(lostModeButton)).click();
		wait.until(ExpectedConditions.elementToBeClickable(By.xpath("//label[text()=\"Next\"]"))).click();
		
		// TODO: clear the textarea
		wait.until(ExpectedConditions
				.elementToBeClickable(By.xpath("//textarea[@aria-label=\"Enter a message (Optional).\"]")))
				.sendKeys(LOST_MODE_MESSAGE);
		driver.findElement(By.xpath("//label[text()=\"Done\"]")).click();
		
		// Calculate time taken to make the button clickable again
		// First, get the spinner
		
		String siblingId = lostModeButton.getAttribute("id").split("-")[0];
		WebElement lostModeSpinner = driver.findElement(By.id(siblingId+"-spinner"));
		long startTime = System.currentTimeMillis();
		// wait.until(ExpectedConditions.invisibilityOf(lostModeSpinner));
		while (true) {
			if (lostModeSpinner.getAttribute("class").contains("sc-hidden"))
				break;
			else 
				Thread.sleep(1000);
		}
		System.out.println((System.currentTimeMillis() - startTime) / 1000 +" seconds");
	}

	static boolean checkIfAlreadyInLostMode() {
		try {
			List<WebElement> lostModeBadges = driver
					.findElements(By.xpath("//label[text()=\"Lost Mode\" and @class=\"fmip-badge-label\"]"));
			boolean isDeviceInLostMode = false;
			for (WebElement badge : lostModeBadges) {
				if (badge.isDisplayed()) {
					isDeviceInLostMode = true;
					break;
				}
			}
			return isDeviceInLostMode;
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
	}

	static WebElement getActiveLostModeButton() {
		List<WebElement> lostModeButtons = driver.findElements(By.xpath("//label[text()=\"Lost Mode\"]"));

		for (WebElement btn : lostModeButtons) {
			if (btn.isDisplayed())
				return btn;
		}

		return null;
	}

	static void logout() {
		try {
			wait.until(ExpectedConditions.elementToBeClickable(By.xpath("//*[@title=\"iCloud Settings & Sign Out\"]")))
					.click();
			wait.until(ExpectedConditions.elementToBeClickable(By.linkText("Sign Out"))).click();
			wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("auth-frame")));
			wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("auth-frame")));
		} catch (Exception e) {
			System.out.println("Exception while logging out");
			e.printStackTrace();
		}
	}

	static void openFindIphone() {
		wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//span[text()=\"Find iPhone\"]")));
		driver.navigate().to("https://www.icloud.com/#find");
		// wait.until(ExpectedConditions.elementToBeClickable(By.xpath("//span[text()=\"Find
		// iPhone\"]"))).click();
		wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//iframe[@title=\"Find iPhone\"]")));
		driver.switchTo().frame(driver.findElement(By.xpath("//iframe[@title=\"Find iPhone\"]")));
	}

	static void login() {
		driver.get("https://www.icloud.com/");
		wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("auth-frame")));
		driver.switchTo().frame("auth-frame");
		wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("account_name_text_field")))
				.sendKeys("siddaharthsuman@gmail.com", Keys.ENTER);
		wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("password_text_field"))).sendKeys("Chaos$unny7",
				Keys.ENTER);
	}

}
