import java.awt.Toolkit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;

import org.openqa.selenium.Point;

public class TasksRunner {

	static List<Point> quadrants;
	static int windowWidth;
	static int windowHeight;
	static List<String> deviceList;
	static ExecutorService service;
	static boolean endOfTasks;

	public static void main(String[] args) {
		getScreenDimensions();

		ReentrantLock lock = new ReentrantLock();
		runDeviceFetchSchedule(lock);
		service = Executors.newFixedThreadPool(4);

		for (int i = 0; i < 4; i++) {
			BrowserTasks task = new BrowserTasks(lock);
			service.execute(task);
		}
		service.shutdown();
	}

	static void runDeviceFetchSchedule(ReentrantLock lock) {
		ExecutorService fetcherService = Executors.newSingleThreadExecutor();
		ServerDataFetcher fetcher = new ServerDataFetcher(lock);
		fetcherService.execute(fetcher);
		fetcherService.shutdown();
	}

	static void getScreenDimensions() {
		java.awt.Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
		System.out.println(screenSize.width + " width");
		System.out.println(screenSize.height + " height");
		screenSize.height = screenSize.height - 140;
		screenSize.width = screenSize.width - 800;

		windowWidth = screenSize.width / 2;
		windowHeight = screenSize.height / 2;
		int increments = screenSize.width / 3;

		// Divide in 4 quadrants
		quadrants = new ArrayList<Point>();
		quadrants.add(new Point(0, 10));
		quadrants.add(new Point(increments, 10));
		quadrants.add(new Point(increments * 2, 10));
		quadrants.add(new Point(increments * 3, 10));
	}
}
