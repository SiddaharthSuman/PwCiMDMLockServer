import java.io.FileWriter;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.Keys;
import org.openqa.selenium.Point;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import com.google.gson.Gson;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class BrowserTasks implements Runnable {

	static final String LOST_MODE_MESSAGE = "This device is locked! Please reserve it first or contact the administrator.";

	private static final String DEVICE_IS_IN_LOST_MODE = "Device is in lost mode";
	private static final String DEVICE_IS_ONLINE_AND_PENDING = "Device is online and pending";
	private static final String DEVICE_IS_OFFLINE_AND_PENDING = "Device is offline and pending";
	private static final String DEVICE_IS_IN_PENDING_MODE = "Device is in pending mode";
	private static final String DEVICE_IS_ONLINE = "Device is online";
	private static final String DEVICE_IS_OFFLINE = "Device is offline";
	private static final String DEVICE_NOT_IN_PENDING_MODE = "Device not in pending mode";
	private static final String DEVICE_NOT_IN_LOST_MODE = "Device not in lost mode";
	static final boolean FILE_APPEND = false;
	public static final MediaType MEDIA_TYPE_MARKDOWN = MediaType.parse("application/json");
	WebDriver driver;
	WebDriverWait wait;
	Point location;
	String device;
	ReentrantLock lock;
	FileWriter writer;
	OkHttpClient client;

	public BrowserTasks(ReentrantLock lock) {
		this.lock = lock;
		String driverPath = "/Users/siddaharth.suman/Downloads/Selenium/chromedriver";
		System.setProperty("webdriver.chrome.driver", driverPath);
		driver = new ChromeDriver();
		wait = new WebDriverWait(driver, Dashboard.WAIT_TIMEOUT);
		acquireQuadrantDetails();
		driver.manage().window().setSize(new Dimension(800, 850));
		driver.manage().window().setPosition(location);
		client = new OkHttpClient();
	}

	private void acquireQuadrantDetails() {
		// TODO Auto-generated method stub
		try {
			lock.lock();
			// Acquire the device name
			location = TasksRunner.quadrants.remove(0);
		} catch (Exception e) {
			log("Exception occurred when lock is present!");
			e.printStackTrace();
		} finally {
			lock.unlock();
		}
	}

	private void log(String msg) {
		try {
			writer.write(LocalDateTime.now().toString() + ": ");
			writer.write(msg);
			writer.write("\n");
			writer.flush();

		} catch (Exception e) {
			log("Error while writing to file");
			e.printStackTrace();
		}
	}

	private void closeWriter() {
		try {
			writer.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	public void run() {
		// TODO Auto-generated method stub
		try {
			writer = new FileWriter(Thread.currentThread().getName() + ".txt", FILE_APPEND);

			login();

			openFindIphone();

			while (!TasksRunner.endOfTasks) {
				checkForDevice();
				// releaseDevice();
			}
			// Thread.sleep(10000);
		} catch (TimeoutException e) {
			log("Timeout exception");
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (driver != null) {
				logout();
				driver.quit();
				closeWriter();
			}
		}
	}

	void releaseDevice() {
		try {
			lock.lock();
			TasksRunner.deviceList.add(device);
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			lock.unlock();
		}
	}

	void acquireNewDevice() {
		try {
			// sleep until next wake up if list is empty
			lock.lock();
			while (TasksRunner.deviceList == null || TasksRunner.deviceList.size() <= 0) {
				if (TasksRunner.endOfTasks)
					break;
				System.out.println(Thread.currentThread().getName() + "Sleeping while device list is null");
				// Release lock before sleeping
				lock.unlock();

				// Calculate sleep time here
				long timeElapsed = ChronoUnit.SECONDS.between(ServerDataFetcher.lastFetch, LocalDateTime.now());
				long timeRemaining = ServerDataFetcher.WAIT_PERIOD - timeElapsed + 1;
				System.out.println(
						Thread.currentThread().getName() + " Remaining Time to wait: " + timeRemaining + " seconds");
				Thread.sleep(Math.abs(timeRemaining) * 1000);
				lock.lock();
			}
			if (!TasksRunner.endOfTasks) {
				device = TasksRunner.deviceList.remove(0);
			}

		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			lock.unlock();
		}
	}

	void checkForDevice() {
		acquireNewDevice();

		if (TasksRunner.endOfTasks)
			return;

		log("========Start of report #" + Thread.currentThread().getName() + " for " + device + " =========");

		openDeviceDetails(device);

		StringBuilder deviceStatusText = new StringBuilder();
		try {
			if (!checkIfAlreadyInLostMode()) {
				log(device + ":" + DEVICE_NOT_IN_LOST_MODE);
				deviceStatusText.append(DEVICE_NOT_IN_LOST_MODE);
				if (!checkIfLostModePending()) {
					log(device + ":" + DEVICE_NOT_IN_PENDING_MODE);
					deviceStatusText.append("\n" + DEVICE_NOT_IN_PENDING_MODE);
					// Check if device is offline for a while
					if (checkIfDeviceOffline()) {
						log(device + ":" + DEVICE_IS_OFFLINE);
						deviceStatusText.append("\n" + DEVICE_IS_OFFLINE);
						// Put device in lock state
						putDeviceInLostMode(deviceStatusText);
					} else {
						log(device + ":" + DEVICE_IS_ONLINE);
						deviceStatusText.append("\n" + DEVICE_IS_ONLINE);
						// Put device in lock state
						putDeviceInLostMode(deviceStatusText);
					}
				} else {
					log(device + ":" + DEVICE_IS_IN_PENDING_MODE);
					deviceStatusText.append("\n" + DEVICE_IS_IN_PENDING_MODE);
					if (checkIfDeviceOffline()) {
						log(device + ":" + DEVICE_IS_OFFLINE_AND_PENDING);
						deviceStatusText.append("\n" + DEVICE_IS_OFFLINE_AND_PENDING);
						reportStatusToServer(device, deviceStatusText.toString());
						// Report state of the device to server as Offline with Pending
					} else {
						log(device + ":" + DEVICE_IS_ONLINE_AND_PENDING);
						deviceStatusText.append("\n" + DEVICE_IS_ONLINE_AND_PENDING);
						reportStatusToServer(device, deviceStatusText.toString());
						// Report state of the device to server as Online with Pending
					}
				}
			} else {
				log(device + ":" + DEVICE_IS_IN_LOST_MODE);
				deviceStatusText.append(DEVICE_IS_IN_LOST_MODE);
				reportStatusToServer(device, deviceStatusText.toString());
				// Report state of the device to server as in Lost Mode
			}
		} catch (Exception e) {
			System.out.println("Got an error while checking device");
			log("Got error while checking device");
			e.printStackTrace();
		}
	}

	void openDeviceDetails(String device) {
		// If the device is the same, the dropdown will not go away
		// Check if the device is the same
		WebElement dropdown = wait.until(
				ExpectedConditions.elementToBeClickable(By.xpath("//div[contains(@title, \"Show devices using\")]")));
		dropdown.click();

		// TODO: Scroll to the top till the All devices label is visible
		boolean scrollhandled = false;
		do {

			try {
				wait.withTimeout(Duration.ofMillis(500))
						.until(ExpectedConditions.elementToBeClickable(By.xpath("//div[text()='All Devices']")));
				scrollhandled = true;
			} catch (Exception e) {
				System.out.println("Unable to find the all devices div. Scrolling up...");
				WebElement element = driver.findElement(By.xpath("//div[@class='thumb-center']"));
				Actions builder = new Actions(driver);
				builder.dragAndDropBy(element, 0, -40).build().perform();
			} finally {
				wait.withTimeout(Duration.ofSeconds(Dashboard.WAIT_TIMEOUT));
			}
		} while (!scrollhandled);

		WebElement currentDeviceLabel = dropdown.findElement(By.tagName("label"));
		if (currentDeviceLabel.getText().equals(device)) {
			System.out.println("Currently selected device is " + device + ". Handling...");
			Actions builder = new Actions(driver);
			builder.moveToElement(driver.findElement(By.xpath("//div[contains(@class, 'fmip-modal-pane')]")), 0, 0)
					.click().build().perform();
		} else {
			// handle stale element exception here
			boolean handled = false;
			do {
				try {
					wait.withTimeout(Duration.ofMillis(500)).until(
							ExpectedConditions.elementToBeClickable(By.xpath("//div[@title=\"" + device + "\"]")))
							.click();

					// Handle if the user is presented with a popup for device offline
					// div[contains(@class, 'find-me')]/label[text()='OK']
					try {
						Thread.sleep(1000);
						WebElement suddenOKPopup = wait.withTimeout(Duration.ofSeconds(1))
								.until(ExpectedConditions.visibilityOfElementLocated(
										By.xpath("//div[contains(@class, 'find-me')]/label[text()='OK']")));

						suddenOKPopup.click();
						System.out.println("Clicked on sudden ok");
					} catch (Exception e) {
						log("Could not find the sudden ok popup");
						// e.printStackTrace();
					} finally {
						// reset the modified timeout
						wait.withTimeout(Duration.ofSeconds(Dashboard.WAIT_TIMEOUT));
					}

					handled = true;
				} catch (StaleElementReferenceException e) {
					log("Handling stale element reference exception");
					handled = false;
				} catch (TimeoutException e) {
					System.out.println("Time out exception for device, probably invisible in the list. Handling...");
					WebElement element = driver.findElement(By.xpath("//div[@class='thumb-center']"));
					Actions builder = new Actions(driver);
					builder.dragAndDropBy(element, 0, 40).build().perform();
				} finally {
					wait.withTimeout(Duration.ofSeconds(Dashboard.WAIT_TIMEOUT));
				}
			} while (!handled);
		}
	}

	void login() {
		driver.get("https://www.icloud.com/");
		wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("auth-frame")));
		driver.switchTo().frame("auth-frame");
		wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("account_name_text_field")))
				.sendKeys(Helpers.username, Keys.ENTER);
		wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("password_text_field")))
				.sendKeys(Helpers.password, Keys.ENTER);
	}

	void logout() {
		try {
			wait.until(ExpectedConditions.elementToBeClickable(By.xpath("//*[@title=\"iCloud Settings & Sign Out\"]")))
					.click();
			wait.until(ExpectedConditions.elementToBeClickable(By.linkText("Sign Out"))).click();
			wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("auth-frame")));
			wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("auth-frame")));
		} catch (Exception e) {
			log("Exception while logging out");
			e.printStackTrace();
		}
	}

	void openFindIphone() {
		wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//span[text()=\"Find iPhone\"]")));
		driver.navigate().to("https://www.icloud.com/#find");
		// wait.until(ExpectedConditions.elementToBeClickable(By.xpath("//span[text()=\"Find
		// iPhone\"]"))).click();
		wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//iframe[@title=\"Find iPhone\"]")));
		driver.switchTo().frame(driver.findElement(By.xpath("//iframe[@title=\"Find iPhone\"]")));
	}

	boolean checkIfAlreadyInLostMode() {
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

	boolean checkIfLostModePending() {
		try {

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
			log("Could not find lost mode pending text");
			e.printStackTrace();
			return false;
		}
	}

	boolean checkIfDeviceOffline() {
		try {
			try {
				// Check if green/black location dot is present
				wait.withTimeout(Duration.ofSeconds(1)).until(ExpectedConditions
						.visibilityOfElementLocated(By.xpath("//div[contains(@class, \'device-annotation\')]")));

				WebElement locationDot = driver.findElement(By.xpath("//div[contains(@class, \'device-annotation\')]"));
				if (locationDot.getAttribute("class").contains("offline")) {
					log("Device was offline recently");
				} else {
					log("Device is online");
				}
				return false;
			} catch (Exception e) {
				log("Could not find the location dot");
				// e.printStackTrace();
			} finally {
				// reset the modified timeout
				wait.withTimeout(Duration.ofSeconds(Dashboard.WAIT_TIMEOUT));
			}

			wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//label[@title=\"Offline\"]")));
			List<WebElement> offlineLabels = driver.findElements(By.xpath("//label[@title=\"Offline\"]"));
			if (offlineLabels.size() > 1) {
				log("There are multiple offline labels");
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
			log("Error while finding offline label");
			e.printStackTrace();
			return false;
		}
	}

	void reportStatusToServer(String device, String status) {
		PostMethod method = new PostMethod();
		method.method = "saveDeviceStatusReport";
		DeviceListForLockingData data = new DeviceListForLockingData();
		data.deviceName = device;
		data.status = status;
		method.data = data;
		Gson gson = new Gson();
		String postbody = gson.toJson(method);
		System.out.println("postbody generated:" + postbody);
		Request request = new Request.Builder().url("http://pwcimdm-server.000webhostapp.com/admin/admin.php")
				.post(RequestBody.create(MEDIA_TYPE_MARKDOWN, postbody)).build();
		client.newCall(request).enqueue(new Callback() {

			@Override
			public void onResponse(Call arg0, Response arg1) throws IOException {
				// TODO Auto-generated method stub
				System.out.println("response from server:" + arg1.body().string());
			}

			@Override
			public void onFailure(Call arg0, IOException arg1) {
				// TODO Auto-generated method stub
				System.out.println("failure on server!");
				arg1.printStackTrace();
			}
		});
	}

	void putDeviceInLostMode(StringBuilder deviceStatusText) throws Exception {
		WebElement lostModeButton = getActiveLostModeButton();
		if (lostModeButton == null)
			throw new Exception("No active lost mode buttons found!");

		wait.until(ExpectedConditions.elementToBeClickable(lostModeButton)).click();
		
		try {
			Thread.sleep(1000);
			WebElement suddenOKPopup = wait.withTimeout(Duration.ofSeconds(1))
					.until(ExpectedConditions.visibilityOfElementLocated(
							By.xpath("//div[contains(@class, 'find-me')]/label[text()='OK']")));

			suddenOKPopup.click();
			System.out.println("Clicked on sudden ok");
		} catch (Exception e) {
			log("Could not find the sudden ok popup");
			// e.printStackTrace();
		} finally {
			// reset the modified timeout
			wait.withTimeout(Duration.ofSeconds(Dashboard.WAIT_TIMEOUT));
		}
		
		try {
			wait.withTimeout(Duration.ofSeconds(1))
					.until(ExpectedConditions.elementToBeClickable(By.xpath("//label[text()=\"Next\"]"))).click();

			// TODO: clear the textarea
			WebElement LostModeMessage = wait.until(ExpectedConditions
					.elementToBeClickable(By.xpath("//textarea[@aria-label=\"Enter a message (Optional).\"]")));
			LostModeMessage.clear();
			LostModeMessage.sendKeys(LOST_MODE_MESSAGE);
			driver.findElement(By.xpath("//label[text()=\"Done\"]")).click();

			// Calculate time taken to make the button clickable again
			// First, get the spinner

			String siblingId = lostModeButton.getAttribute("id").split("-")[0];
			WebElement lostModeSpinner = driver.findElement(By.id(siblingId + "-spinner"));
			long startTime = System.currentTimeMillis();
			// wait.until(ExpectedConditions.invisibilityOf(lostModeSpinner));
			while (true) {
				if (lostModeSpinner.getAttribute("class").contains("sc-hidden"))
					break;
				else
					Thread.sleep(1000);
			}
			System.out.println((System.currentTimeMillis() - startTime) / 1000 + " seconds");
		} catch (Exception e) {
			System.out.println("Exception while finding next button due to sudden popup probably or if already in lost mode");
			try {
				driver.findElement(By.xpath("//label[text()='Stop Lost Mode']"));
				log(device + ":" + DEVICE_IS_IN_LOST_MODE);
				deviceStatusText.append("\n" + DEVICE_IS_IN_LOST_MODE);
			} catch (Exception e1) {
				System.out.println("Could not find stop lost mode button... maybe sudden popup is present");
			}
		} finally {
			wait.withTimeout(Duration.ofSeconds(Dashboard.WAIT_TIMEOUT));
		}
		
		reportStatusToServer(device, deviceStatusText.toString());
	}

	WebElement getActiveLostModeButton() {
		List<WebElement> lostModeButtons = driver.findElements(By.xpath("//label[text()=\"Lost Mode\"]"));

		for (WebElement btn : lostModeButtons) {
			if (btn.isDisplayed())
				return btn;
		}

		return null;
	}

	class DeviceListForLockingData {
		String deviceName;
		String status;

		// @Override
		// public String toString() {
		// Gson gson = new Gson();
		// return gson.toJson(this);
		// }
	}

}
